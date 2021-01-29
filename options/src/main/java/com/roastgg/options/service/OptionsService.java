package com.roastgg.options.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roastgg.options.beans.Price;
import com.roastgg.options.beans.StockData;
import com.roastgg.options.repository.OptionsRepository;
import org.apache.commons.lang.UnhandledException;
import org.apache.commons.math3.analysis.function.Log;
import org.apache.commons.math3.analysis.function.Sqrt;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class OptionsService {

    @Autowired
    OptionsRepository optionsRepository;

    @Value("${stockapi.key}")
    String key;

    public Map<String, Map<String, String>> callService(String symbol) {

        ArrayList<Price> pricelist = new ArrayList<>();

        Flux<Price> options = optionsRepository.findPriceBySymbol(symbol);
        Mono<List<Price>> optionslist = options.collectList();
        pricelist = (ArrayList<Price>)optionslist.share().block();
        DateTime dt = new DateTime(new Date());
        dt = dt.minusDays(4);


        if (pricelist != null && (pricelist.size() == 0 || dt.isAfter(pricelist.get(0).getMarketDate().getTime()))) {
            pricelist = callStockPriceApi(symbol);
        }
        if (null == pricelist || pricelist.size() == 0) {
            throw new UnhandledException(new Throwable("Price List was empty anyway"));
        }

        Map<String, Map<String, String>>  optionsMap = calculateOptionsPrices(pricelist, symbol);

        return optionsMap;
    }

    private ArrayList<Price> callStockPriceApi(String symbol) {

        ArrayList<Price> priceList = new ArrayList<>();

        StringBuilder sb = new StringBuilder();
        sb.append("https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=");
        sb.append(symbol);
        sb.append("&apikey=");
        sb.append(key);

        try {
            WebClient webClient = WebClient.create(sb.toString());
            WebClient.ResponseSpec response1 = webClient.get().retrieve();
            String stockDataMono = response1.bodyToMono(String.class).share().block();

            ObjectMapper mapper = new ObjectMapper();
            StockData data = mapper.readValue(stockDataMono, StockData.class);
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-mm-dd");

            for (String date : data.getDaily().keySet()) {
                Price price = new Price();
                price.setOpen(Double.parseDouble(data.getDaily().get(date).get("1. open")));
                price.setClose(Double.parseDouble(data.getDaily().get(date).get("4. close")));
                price.setHigh(Double.parseDouble(data.getDaily().get(date).get("2. high")));
                price.setLow(Double.parseDouble(data.getDaily().get(date).get("3. low")));
                price.setVolume(Integer.parseInt(data.getDaily().get(date).get("5. volume")));
                price.setMarketDate(formatter.parse(date));
                price.setSymbol(symbol);
                priceList.add(price);
            }

            optionsRepository.saveAll(priceList).subscribe();

        } catch (Exception e) {
            throw new UnhandledException(e);
        }
        return priceList;
    }

    private Map<String, Map<String,String>> calculateOptionsPrices(ArrayList<Price> prices, String symbol) {

        Map<String, Map<String, String>> optionsMap = new LinkedHashMap<>();

        Collections.sort(prices, new SortByDate());
        Integer numberofcycles = prices.size();

        Double std = calculateLogStd(prices, numberofcycles);
        LinkedHashMap<String, Double> expiryTimes = calculateExpiryTimes();
        Double stockCurrentPrice = getStockCurrentPrice(symbol);
        ArrayList<Double> strikePrices = calculateStrikePrice(stockCurrentPrice);
        Double dividend = callDividendApi(symbol);

        for(Double price : strikePrices) {
            Map<String, String> pricesInTime = new LinkedHashMap<>();
            for (String date : expiryTimes.keySet()) {

                pricesInTime.put(date, calculateBS(price, expiryTimes.get(date), std, stockCurrentPrice, dividend).toString());
            }
            optionsMap.put(price.toString(), pricesInTime);
        }

        return optionsMap;
    }

    private Double calculateLogStd(ArrayList<Price> prices, Integer numberofcycles) {

        double[] closingPrices = new double[prices.size()-1];
        Log log = new Log();

        for(int i = 0; i < prices.size()-1; i++) {
            double d = (double)prices.get(i).getClose();

            closingPrices[i] = log.value(prices.get(i).getClose()/prices.get(i+1).getClose());
        }

        StandardDeviation standardDeviation = new StandardDeviation();
        double std = standardDeviation.evaluate(closingPrices);

        //For annualized returns
        return std * Math.sqrt(252/numberofcycles);
    }

    private LinkedHashMap<String, Double> calculateExpiryTimes() {

        Long secondsInYear = 31556952000L;

        Integer numberofweeks = 150;

        DateTime dt = new DateTime(new Date());
        Integer dayofweek = dt.getDayOfWeek();
        DateTime now = new DateTime(new Date());
        DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd");

        while(dayofweek != 5) {
            dt = dt.plusDays(1);
            dayofweek = dt.getDayOfWeek();
        }

        LinkedHashMap<String, Double> daysinseconds = new LinkedHashMap<>();
        for(int i = 0; i < numberofweeks; i++) {
            daysinseconds.put(dt.toString(dateTimeFormatter), Double.valueOf(dt.getMillis() - now.getMillis())/secondsInYear);
            dt = dt.plusDays(7);
        }

        return daysinseconds;
    }

    private Double getStockCurrentPrice(String symbol) {

        StringBuilder sb = new StringBuilder();
        sb.append("https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=");
        sb.append(symbol);
        sb.append("&apikey=");
        sb.append(key);

        try {
            WebClient webClient = WebClient.create(sb.toString());
            WebClient.ResponseSpec response1 = webClient.get().retrieve();
            Map<String, Map<String,String>> price = response1.bodyToMono(Map.class).share().block();

            return Double.parseDouble(price.get("Global Quote").get("05. price"));

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return 0.0;

    }

    private ArrayList<Double> calculateStrikePrice(Double price) {
        Double step = 1.0;
        if (price < 50) {
            step = 0.5;
        }
        price = Double.valueOf(price.intValue());
        Double low = price - step;

        ArrayList<Double> strikeprices = new ArrayList<>();

        for (int i = 0; i < 17; i++) {
            strikeprices.add(low);
            strikeprices.add(price);
            low = low - step;
            price = price + step;
        }

        Collections.sort(strikeprices);
        return strikeprices;

    }

    private Double calculateBS(Double strike, Double expiry, Double std, Double currentprice, Double dividend) {

        Double optionPrice = 0.0;
        Double r = 0.0008;

        Double d1 = calculateD1(currentprice, strike, expiry, std, dividend);
        Double d2 = calculateD2(currentprice, strike, expiry, std, dividend);

        NormalDistribution normalDistribution = new NormalDistribution();

        Double discountedprice = currentprice * Math.exp(-dividend * expiry);

        Double weightprice = discountedprice *  normalDistribution.cumulativeProbability(d1);
        Double discountedStrikePrice = strike * Math.exp(-r * expiry);

        Double weightedstrikeprice = discountedStrikePrice * normalDistribution.cumulativeProbability(d2);

        return weightprice - weightedstrikeprice;

    }

    private Double calculateD1(Double currentprice, Double strike, Double expiry, Double std, Double dividend) {
        Double r = 0.0008;
        Log log = new Log();
        Sqrt sqrt = new Sqrt();

        Double numerator = log.value(currentprice/strike) + ((r - dividend + ((std*std))/2) * expiry);
        Double denominator = std * sqrt.value(expiry);

        return numerator/denominator;
    }

    private Double calculateD2(Double currentprice, Double strike, Double expiry, Double std, Double dividend) {
        Double r = 0.0008;

        Log log = new Log();
        Sqrt sqrt = new Sqrt();

        Double numerator = log.value(currentprice/strike) + ((r - dividend - ((std*std)/2)) * expiry);
        Double denominator = std * sqrt.value(expiry);

        return numerator/denominator;
    }

    private Double callDividendApi(String symbol) {

        StringBuilder sb = new StringBuilder();
        sb.append("https://www.alphavantage.co/query?function=OVERVIEW&symbol=");
        sb.append(symbol);
        sb.append("&apikey=");
        sb.append(key);

        try {
            WebClient webClient = WebClient.create(sb.toString());
            WebClient.ResponseSpec response1 = webClient.get().retrieve();
            Map<String, String> overview = response1.bodyToMono(Map.class).share().block();

            return Double.parseDouble(overview.get("DividendYield"));

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return 0.0;

    }

    class SortByDate implements Comparator<Price> {

        public int compare(Price a, Price b) {
            return a.getMarketDate().compareTo(b.getMarketDate());
        }
    }
}
