package com.roastgg.options.repository;

import com.roastgg.options.beans.Price;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface OptionsRepository extends ReactiveCassandraRepository<Price, Integer> {

    Flux<Price> findPriceBySymbol(String symbol);

}
