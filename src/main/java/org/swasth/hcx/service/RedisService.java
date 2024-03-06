package org.swasth.hcx.service;

import org.swasth.hcx.exception.ErrorCodes;
import org.swasth.hcx.exception.ServerException;
import org.swasth.hcx.utils.JSONUtils;
import redis.clients.jedis.Jedis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RedisService {

    private final String redisHost;
    private final int redisPort;

    public RedisService(String redisHost, int redisPort) {
        this.redisHost = redisHost;
        this.redisPort = redisPort;
    }

    public Jedis getConnection() throws Exception {
        try {
            return new Jedis(redisHost, redisPort);
        } catch (Exception e) {
            throw new ServerException(ErrorCodes.INTERNAL_SERVER_ERROR, "Error connecting to redis server " + e);
        }
    }

    public void set(String key, String value, int ttl) throws Exception {
        System.out.println("setting the key :" + key  + " for value :" + value);
        try (Jedis jedis = getConnection()) {
            jedis.setex(key, ttl, value);
        } catch (Exception e) {
            throw new ServerException(ErrorCodes.INTERNAL_SERVER_ERROR, "Exception Occurred While Saving Data to Redis Cache for Key : " + key + "| Exception is:" + e);
        }
    }

    public List<Map<String, Object>> get(String key) throws Exception {
        List<Map<String, Object>> notificationList = new ArrayList<>();
        try (Jedis jedis = getConnection()) {
            Set<String> matchingKeys = jedis.keys("*" + key + "*");
            if (matchingKeys.isEmpty()) {
                return notificationList;
            }
//          List<String> sortedKeys = new ArrayList<>(matchingKeys);
//          sortedKeys.sort((k1, k2) -> (int) (jedis.ttl(k2) - jedis.ttl(k1)));
            List<String> matchingValues = jedis.mget(matchingKeys.toArray(new String[0]));
            for (String keys : matchingValues) {
                notificationList.add(JSONUtils.deserialize(keys, Map.class));
            }
            System.out.println("Notification list for the participant code :" + key + " will be :" + notificationList);
            return notificationList;
        } catch (Exception e) {
            throw new ServerException(ErrorCodes.INTERNAL_SERVER_ERROR, "Exception Occurred While Fetching Data from Redis Cache for Key : " + key + "| Exception is:" + e.getMessage());
        }
    }
}