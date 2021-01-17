package com.roastgg.options.controller;

import com.roastgg.options.service.OptionsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class OptionsController {

    @Autowired
    OptionsService optionsService;

    @RequestMapping("/call")
    public ResponseEntity<Map<String, Map<String, String>>> callController(@RequestParam String symbol) {

        if (symbol.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        System.out.println(symbol);
        Map<String, Map<String, String>> optionsmap;
        optionsmap = optionsService.callService(symbol);
        if (null == optionsmap) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>(optionsmap, HttpStatus.OK);

    }
}
