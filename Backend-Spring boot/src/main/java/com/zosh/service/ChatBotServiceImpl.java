package com.zosh.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ReadContext;
import com.zosh.model.Coin;
import com.zosh.model.CoinDTO;
import com.zosh.response.ApiResponse;
import com.zosh.response.FunctionResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
public class ChatBotServiceImpl implements ChatBotService{

    @Value("${gemini.api.key}")
    private String API_KEY;

    private double convertToDouble(Object value) {
        if (value instanceof Integer) {
            return ((Integer) value).doubleValue();
        } else if (value instanceof Long) {
            return ((Long) value).doubleValue();
        } else if (value instanceof Double) {
            return (Double) value;
        } else {
            throw new IllegalArgumentException("Unsupported data type: " + value.getClass().getName());
        }
    }

    public CoinDTO makeApiRequest(String currencyName) {
        String url = "https://api.coingecko.com/api/v3/coins/" + currencyName.toLowerCase();
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> responseEntity = restTemplate.getForEntity(url, Map.class);
        Map<String, Object> responseBody = responseEntity.getBody();

        if (responseBody != null) {
            Map<String, Object> image = (Map<String, Object>) responseBody.get("image");
            Map<String, Object> marketData = (Map<String, Object>) responseBody.get("market_data");

            CoinDTO coinInfo = new CoinDTO();
            coinInfo.setId((String) responseBody.get("id"));
            coinInfo.setSymbol((String) responseBody.get("symbol"));
            coinInfo.setName((String) responseBody.get("name"));
            coinInfo.setImage((String) image.get("large"));
            coinInfo.setCurrentPrice(convertToDouble(((Map<String, Object>) marketData.get("current_price")).get("usd")));
            coinInfo.setMarketCap(convertToDouble(((Map<String, Object>) marketData.get("market_cap")).get("usd")));
            coinInfo.setMarketCapRank((int) responseBody.get("market_cap_rank"));
            coinInfo.setTotalVolume(convertToDouble(((Map<String, Object>) marketData.get("total_volume")).get("usd")));
            coinInfo.setHigh24h(convertToDouble(((Map<String, Object>) marketData.get("high_24h")).get("usd")));
            coinInfo.setLow24h(convertToDouble(((Map<String, Object>) marketData.get("low_24h")).get("usd")));
            coinInfo.setPriceChange24h(convertToDouble(marketData.get("price_change_24h")));
            coinInfo.setPriceChangePercentage24h(convertToDouble(marketData.get("price_change_percentage_24h")));
            coinInfo.setMarketCapChange24h(convertToDouble(marketData.get("market_cap_change_24h")));
            coinInfo.setMarketCapChangePercentage24h(convertToDouble(marketData.get("market_cap_change_percentage_24h")));
            coinInfo.setCirculatingSupply(convertToDouble(marketData.get("circulating_supply")));
            coinInfo.setTotalSupply(convertToDouble(marketData.get("total_supply")));

            return coinInfo;
        }

        return null;
    }

    public FunctionResponse getFunctionResponse(String prompt) {
        String GEMINI_API_URL = "z" + API_KEY;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String requestBody = "{\n" +
                "  \"contents\": [\n" +
                "    {\n" +
                "      \"parts\": [\n" +
                "        {\n" +
                "          \"text\": \"" + prompt + "\"\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  ],\n" +
                "  \"tools\": [\n" +
                "    {\n" +
                "      \"functionDeclarations\": [\n" +
                "        {\n" +
                "          \"name\": \"getCoinDetails\",\n" +
                "          \"description\": \"Get the coin details from given currency object\",\n" +
                "          \"parameters\": {\n" +
                "            \"type\": \"OBJECT\",\n" +
                "            \"properties\": {\n" +
                "              \"currencyName\": {\n" +
                "                \"type\": \"STRING\",\n" +
                "                \"description\": \"The currency name, id, symbol.\"\n" +
                "              },\n" +
                "              \"currencyData\": {\n" +
                "                \"type\": \"STRING\",\n" +
                "                \"description\": \"Currency Data id, symbol, name, image, current_price, market_cap, market_cap_rank, fully_diluted_valuation, total_volume, high_24h, low_24h, price_change_24h, price_change_percentage_24h, market_cap_change_24h, market_cap_change_percentage_24h, circulating_supply, total_supply, max_supply, ath, ath_change_percentage, ath_date, atl, atl_change_percentage, atl_date, last_updated.\"\n" +
                "              }\n" +
                "            },\n" +
                "            \"required\": [\"currencyName\", \"currencyData\"]\n" +
                "          }\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.postForEntity(GEMINI_API_URL, requestEntity, String.class);

        String responseBody = response.getBody();
        ReadContext ctx = JsonPath.parse(responseBody);

        // Check if functionCall exists
        if (ctx.read("$.candidates[0].content.parts[0].functionCall", Object.class) != null) {
            String currencyName = ctx.read("$.candidates[0].content.parts[0].functionCall.args.currencyName");
            String currencyData = ctx.read("$.candidates[0].content.parts[0].functionCall.args.currencyData");
            String name = ctx.read("$.candidates[0].content.parts[0].functionCall.name");

            FunctionResponse res = new FunctionResponse();
            res.setCurrencyName(currencyName);
            res.setCurrencyData(currencyData);
            res.setFunctionName(name);

            return res;
        } else {
            // Handle the case where functionCall is not present
            // For example, extract the text response
            String text = ctx.read("$.candidates[0].content.parts[0].text");
            FunctionResponse res = new FunctionResponse();
            res.setFunctionName("textResponse");
            res.setCurrencyData(text);
            return res;
        }
    }


    @Override
    public ApiResponse getCoinDetails(String prompt) {
        FunctionResponse res = getFunctionResponse(prompt);

        CoinDTO coin = makeApiRequest(res.getCurrencyName());
        if (coin == null) {
            return new ApiResponse("Failed to retrieve coin data.", false);
        }

        ObjectMapper mapper = new ObjectMapper();
        String coinJson;
        try {
            coinJson = mapper.writeValueAsString(coin);
            coinJson = coinJson.replace("\"", "\\\""); // Escape quotes for JSON string
        } catch (Exception e) {
            return new ApiResponse("Error processing coin data.", false);
        }

        String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=" + API_KEY;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String finalBody = "{\n" +
                "  \"contents\": [\n" +
                "    {\n" +
                "      \"role\": \"user\",\n" +
                "      \"parts\": [{ \"text\": \"" + prompt + "\" }]\n" +
                "    },\n" +
                "    {\n" +
                "      \"role\": \"model\",\n" +
                "      \"parts\": [{\n" +
                "        \"functionCall\": {\n" +
                "          \"name\": \"getCoinDetails\",\n" +
                "          \"args\": {\n" +
                "            \"currencyName\": \"" + res.getCurrencyName() + "\",\n" +
                "            \"currencyData\": \"" + res.getCurrencyData().replace("\"", "\\\"") + "\"\n" +
                "          }\n" +
                "        }\n" +
                "      }]\n" +
                "    },\n" +
                "    {\n" +
                "      \"role\": \"function\",\n" +
                "      \"parts\": [{\n" +
                "        \"functionResponse\": {\n" +
                "          \"name\": \"getCoinDetails\",\n" +
                "          \"response\": {\n" +
                "            \"name\": \"getCoinDetails\",\n" +
                "            \"content\": \"" + coinJson + "\"\n" +
                "          }\n" +
                "        }\n" +
                "      }]\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        HttpEntity<String> requestEntity = new HttpEntity<>(finalBody, headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> geminiResponse = restTemplate.postForEntity(GEMINI_API_URL, requestEntity, String.class);

        return new ApiResponse(geminiResponse.getBody(), true);
    }

    @Override
    public CoinDTO getCoinByName(String coinName) {
        return this.makeApiRequest(coinName);
//        return null;
    }

    @Override
    public String simpleChat(String prompt) {

        String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=" + API_KEY;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Construct the request body using JSONObject
        JSONObject requestBody = new JSONObject();
        JSONArray contentsArray = new JSONArray();
        JSONObject contentsObject = new JSONObject();
        JSONArray partsArray = new JSONArray();
        JSONObject textObject = new JSONObject();
        textObject.put("text", prompt);
        partsArray.put(textObject);
        contentsObject.put("parts", partsArray);
        contentsArray.put(contentsObject);
        requestBody.put("contents", contentsArray);

        // Create the HTTP entity with headers and request body
        HttpEntity<String> requestEntity = new HttpEntity<>(requestBody.toString(), headers);

        // Make the POST request
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.postForEntity(GEMINI_API_URL, requestEntity, String.class);


        String responseBody = response.getBody();

        System.out.println("Response Body: " + responseBody);

        return responseBody;
    }


}
