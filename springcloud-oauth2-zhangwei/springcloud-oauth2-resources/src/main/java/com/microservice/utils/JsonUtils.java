package com.microservice.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author zhangwei
 * @date 2022-06-20
 * JSON工具类
 */
@Slf4j
public class JsonUtils {
    private static ObjectMapper mapper = new ObjectMapper();

    public static ObjectMapper getMapper() {
        return mapper;
    }

    static {
        mapper.registerModule(new ParameterNamesModule())
                .registerModule(new Jdk8Module())
                .registerModule(new JavaTimeModule());

        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    /**
     * JSON 转 集合(集合元素可以为:基本数据类型, Object)
     *
     * @param data
     * @param cls
     * @return
     * @throws IOException
     */
    public static List<?> transformCollectionsFromJson(String data, Class cls, Class<? extends Collection> collection) {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);

        CollectionType collectionType = mapper.getTypeFactory().constructCollectionType(collection, cls);
        try {
            return mapper.readValue(data, collectionType);
        } catch (IOException e) {
            log.error("String:{} To Collection:{} error, reason: {}", data, collection, e.getMessage(), e);
            return null;
        }
    }

    /**
     * JSON 转 集合(集合元素可以为:Map)
     *
     * @param data
     * @param cls
     * @return
     * @throws IOException
     */
    public static List<?> transformCollectionsFromJson(String data, Class<? extends Map> cls) {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        CollectionType collectionType = mapper.getTypeFactory().constructCollectionType(ArrayList.class, cls);
        try {
            return mapper.readValue(data, collectionType);
        } catch (IOException e) {
            log.error("String:{} To Map:{} error, reason: {}", data, cls, e.getMessage(), e);
            return null;
        }
    }

    /**
     * JSON 转 对象
     *
     * @param data
     * @param cls
     * @param <T>
     * @return
     * @throws IOException
     */
    public static <T> T fromJson(String data, Class<T> cls) {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        try {
            return mapper.readValue(data, cls);
        } catch (IOException e) {
            log.error("String:{} To Object:{} error, reason: {}", data, cls, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 将Map转成指定的Bean
     *
     * @param map
     * @param clazz
     * @return
     * @throws Exception
     */
    public static Object mapToBean(Map map, Class clazz) {
        try {
            return mapper.readValue(objectToString(map), clazz);
        } catch (IOException e) {
            log.error("Map:{} To Bean:{} error, reason: {}", map, clazz, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 将对象转成字符串
     *
     * @param obj
     * @return
     * @throws Exception
     */
    public static String objectToString(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Object:{} To String error, reason: {}", obj, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 对象 转 JSON
     *
     * @param protocol
     * @return
     */
    public static String toJson(Object protocol) {
        try {
            return mapper.writeValueAsString(protocol);
        } catch (JsonProcessingException e) {
            log.error("Object:{} To Json error, reason: {}", protocol, e.getMessage(), e);
            return null;
        }
    }
}
