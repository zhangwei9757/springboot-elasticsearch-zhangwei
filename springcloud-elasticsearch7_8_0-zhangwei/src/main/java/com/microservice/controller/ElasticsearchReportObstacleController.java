package com.microservice.controller;

import com.google.common.base.Stopwatch;
import com.microservice.dto.ReportObstacleDto;
import com.microservice.dto.ResponseDto;
import com.microservice.utils.JsonUtils;
import com.microservice.utils.RandomUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.*;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.index.query.MatchPhraseQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author zhangwei
 * @date 2020-06-28
 * <p>
 */
@RestController
@RequestMapping(value = "/es")
@Api(value = "ES全文搜索", tags = "ES全文搜索")
@Slf4j
public class ElasticsearchReportObstacleController {

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    /**
     * 创建索引
     *
     * @param indexName
     * @param mappings
     * @return
     * @throws Exception
     */
    @GetMapping("/createIndex")
    @PostMapping(value = "/create")
    @ApiOperation(value = "创建索引", tags = "ES全文搜索")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "indexName", value = "索引名", required = true, dataType = "String", paramType = "query"),
            @ApiImplicitParam(name = "mappings", value = "映射", required = true, dataType = "String", paramType = "query", defaultValue = "{\n" +
                    "  \"properties\": {\n" +
                    "    \"obstacleNo\": {\n" +
                    "      \"type\":\"long\",\n" +
                    "      \"store\": true\n" +
                    "    },\n" +
                    "    \"obstacleTitle\": {\n" +
                    "      \"type\": \"text\",\n" +
                    "      \"store\": true,\n" +
                    "      \"analyzer\": \"ik_smart\"\n" +
                    "    },\n" +
                    "    \"obstacleDesc\": {\n" +
                    "      \"type\": \"text\",\n" +
                    "      \"store\": true,\n" +
                    "      \"analyzer\": \"ik_smart\"\n" +
                    "    },\n" +
                    "    \"obstacleTime\": {\n" +
                    "      \"type\": \"long\",\n" +
                    "      \"store\": true\n" +
                    "    },\n" +
                    "    \"systemName\": {\n" +
                    "      \"type\": \"text\",\n" +
                    "      \"store\": true,\n" +
                    "      \"analyzer\": \"ik_smart\"\n" +
                    "    },\n" +
                    "    \"moduleName\": {\n" +
                    "      \"type\": \"text\",\n" +
                    "      \"store\": true,\n" +
                    "      \"analyzer\": \"ik_smart\"\n" +
                    "    }\n" +
                    "  }\n" +
                    "}")
    })
    public String createIndex(@RequestParam("indexName") String indexName, @RequestParam("mappings") String mappings) throws Exception {
        if (StringUtils.isBlank(indexName)) {
            return "索引名不能为空";
        }

        CreateIndexRequest indexRequest = new CreateIndexRequest(indexName);
        Settings build = Settings.builder()
                .put("number_of_shards", "5")
                .put("number_of_replicas", "1")
                .build();
        indexRequest.settings(build);

        if (!StringUtils.isBlank(mappings)) {
            indexRequest.mapping(mappings, XContentType.JSON);
        }

        CreateIndexResponse createIndexResponse = restHighLevelClient.indices().create(indexRequest, RequestOptions.DEFAULT);
        boolean acknowledged = createIndexResponse.isAcknowledged();
        indexName += acknowledged ? ",创建成功" : ",创建失败";
        return indexName;
    }


    /**
     * 删除索引
     *
     * @param
     * @return
     */
    @PostMapping(value = "/deleteIndex")
    @ApiOperation(value = "删除索引", tags = "ES全文搜索")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "indexName", value = "索引名", required = true, dataType = "String", paramType = "query")
    })
    public String deleteIndex(@RequestParam("indexName") String indexName) throws Exception {
        if (StringUtils.isBlank(indexName)) {
            return "索引名不能为空";
        }

        DeleteIndexRequest deleteRequest = new DeleteIndexRequest(indexName);
        AcknowledgedResponse delete = restHighLevelClient.indices().delete(deleteRequest, RequestOptions.DEFAULT);
        boolean acknowledged = delete.isAcknowledged();
        return acknowledged ? "删除成功" : "删除失败";
    }

    /**
     * 批量创建文档
     *
     * @param
     * @return
     */
    @PostMapping(value = "/addDocumnet")
    @ApiOperation(value = "批量创建文档", tags = "ES全文搜索")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "indexName", value = "索引名", required = true, dataType = "String", paramType = "query"),
            @ApiImplicitParam(name = "contents", value = "文档内容", required = true, dataType = "String", paramType = "query"),
            @ApiImplicitParam(name = "start_num", value = "开始值", required = true, dataType = "int", paramType = "query"),
            @ApiImplicitParam(name = "end_num", value = "结束值", required = true, dataType = "int", paramType = "query")
    })
    public String addDocumnet(@RequestParam("indexName") String indexName,
                              @RequestParam("contents") String contents,
                              @RequestParam("start_num") int start_num,
                              @RequestParam("end_num") int end_num) throws Exception {
        if (StringUtils.isBlank(indexName)) {
            return "索引名不能为空";
        }

        if (StringUtils.isBlank(contents)) {
            return "内容不能为空";
        }

        if (start_num <= 0 || end_num <= 0 || end_num - start_num <= 0) {
            return "生成数据不能小于0";
        }

        long start = System.currentTimeMillis();
        int dist = end_num - start_num + 1;
        int pageSize = dist >= 10000 ? 10000 : dist;
        int current = start_num;

        while (true){
            int temp = current + pageSize;
            boolean esLoadData = this.esLoadData(current, temp, contents, indexName);

            current = temp;
            if (!esLoadData) {
                return "添加失败";
            }
            if (current >= end_num) {
                break;
            }

            int diff = end_num - temp;
            if (diff < pageSize) {
                pageSize = diff;
            }
        }

        long end = System.currentTimeMillis();
        return String.format("添加成功, 共: %s 条数据, 耗时: %s 秒", dist, (end - start) / 1000);
    }

    /**
     * 查询文档
     *
     * @param indexName
     * @return
     * @throws Exception
     */
    @PostMapping(value = "/getDocument")
    @ApiOperation(value = "查询文档", tags = "ES全文搜索")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "indexName", value = "索引名", required = true, dataType = "String", paramType = "query"),
            @ApiImplicitParam(name = "queryField", value = "查询字段", required = false, dataType = "String", paramType = "query"),
            @ApiImplicitParam(name = "queryContent", value = "查询内容", required = false, dataType = "String", paramType = "query"),
            @ApiImplicitParam(name = "readOnlyCount", value = "只显示数量", required = true, dataType = "boolean", paramType = "query")
    })
    public Object getDocument(@RequestParam("indexName") String indexName,
                              @RequestParam(value = "queryField", required = false) String queryField,
                              @RequestParam(value = "queryContent", required = false) String queryContent,
                              @RequestParam("readOnlyCount") boolean readOnlyCount) throws Exception {
        if (StringUtils.isBlank(indexName)) {
            return "索引名不能为空";
        }
        Stopwatch stopwatch = Stopwatch.createStarted();
        SearchRequest searchRequest = new SearchRequest(indexName);

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.timeout(TimeValue.timeValueSeconds(5));
        searchSourceBuilder.size(10000);

        if (StringUtils.isBlank(queryField)) {
            MatchAllQueryBuilder matchAllQueryBuilder = QueryBuilders.matchAllQuery();
            searchSourceBuilder.query(matchAllQueryBuilder);
        } else {
            MatchPhraseQueryBuilder matchQueryBuilder = QueryBuilders.matchPhraseQuery(queryField, queryContent);
            searchSourceBuilder.query(matchQueryBuilder);
        }

        searchRequest.source(searchSourceBuilder);
        SearchResponse search = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        long elapsed = stopwatch.elapsed(TimeUnit.MILLISECONDS);
        stopwatch.stop();
        SearchHit[] hits = search.getHits().getHits();
        List<Map<String, Object>> collect = Arrays.stream(hits).map(SearchHit::getSourceAsMap).collect(Collectors.toList());
        return hits.length > 0 ? ResponseDto.success(readOnlyCount ? collect.size() : collect, elapsed) : ResponseDto.success("无结果", elapsed);
    }

    /**
     * 查询所有文档 Scroll
     *
     * @param indexName
     * @return
     * @throws Exception
     */
    @PostMapping(value = "/getDocuments")
    @ApiOperation(value = "scroll查询所有文档[谨慎使用]", tags = "ES全文搜索")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "indexName", value = "索引名", required = true, dataType = "String", paramType = "query"),
            @ApiImplicitParam(name = "readOnlyCount", value = "只显示数量", required = true, dataType = "boolean", paramType = "query")
    })
    public ResponseDto getDocuments(@RequestParam("indexName") String indexName,
                                    @RequestParam("readOnlyCount") boolean readOnlyCount) throws Exception {
        if (StringUtils.isBlank(indexName)) {
            return ResponseDto.error("索引名不能为空");
        }
        Stopwatch stopwatch = Stopwatch.createStarted();
        SearchRequest searchRequest = new SearchRequest(indexName);
        searchRequest.scroll(TimeValue.timeValueSeconds(60));

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.timeout(TimeValue.timeValueSeconds(60));
        searchSourceBuilder.size(10000);
        searchSourceBuilder.sort("troubleTime", SortOrder.DESC);

        searchRequest.source(searchSourceBuilder);
        SearchResponse search = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

        String scrollId = search.getScrollId();
        List<String> scrollIds = new ArrayList<>();
        scrollIds.add(scrollId);
        String temp = scrollId;

        SearchHit[] hits = search.getHits().getHits();
        if (hits.length >= 10000) {
            ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
            clearScrollRequest.setScrollIds(scrollIds);
            restHighLevelClient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
            return ResponseDto.error("总数已达到10000, 禁止查询所有");
        }
        List<Map<String, Object>> collect = Arrays.stream(hits).map(SearchHit::getSourceAsMap).collect(Collectors.toList());

        while (true) {
            SearchScrollRequest scrollRequest = new SearchScrollRequest(temp);
            scrollRequest.scroll(TimeValue.timeValueSeconds(60));

            SearchResponse scroll = restHighLevelClient.scroll(scrollRequest, RequestOptions.DEFAULT);
            SearchHit[] hits1 = scroll.getHits().getHits();
            if (hits1 == null || hits1.length <= 0) {
                break;
            }
            collect.addAll(Arrays.stream(hits1).map(SearchHit::getSourceAsMap).collect(Collectors.toList()));
            String scrollId1 = scroll.getScrollId();
            if (!Objects.deepEquals(scrollId1, temp)) {
                temp = scrollId1;
                scrollIds.add(scrollId1);
            }
        }
        long elapsed = stopwatch.elapsed(TimeUnit.MILLISECONDS);
        stopwatch.stop();
        ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
        clearScrollRequest.setScrollIds(scrollIds);
        ClearScrollResponse clearScrollResponse = restHighLevelClient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
        boolean succeeded = clearScrollResponse.isSucceeded();
        if (readOnlyCount) {
            return succeeded ? ResponseDto.success(collect.size(), elapsed) : ResponseDto.error("查询失败", elapsed);
        }
        return succeeded ? ResponseDto.success(collect, elapsed) : ResponseDto.error("查询失败", elapsed);
    }

    /**
     * 删除文档
     *
     * @param indexName
     * @return
     * @throws Exception
     */
    @PostMapping(value = "/deleteDocument")
    @ApiOperation(value = "删除文档", tags = "ES全文搜索")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "indexName", value = "索引名", required = true, dataType = "String", paramType = "query"),
            @ApiImplicitParam(name = "queryField", value = "查询字段", required = false, dataType = "String", paramType = "query"),
            @ApiImplicitParam(name = "queryContent", value = "查询内容", required = false, dataType = "String", paramType = "query")
    })
    public Object deleteDocument(@RequestParam("indexName") String indexName,
                                 @RequestParam(value = "queryField", required = false) String queryField,
                                 @RequestParam(value = "queryContent", required = false) String queryContent) throws Exception {
        if (StringUtils.isBlank(indexName)) {
            return "索引名不能为空";
        }

        DeleteByQueryRequest deleteRequest = new DeleteByQueryRequest(indexName);
        deleteRequest.setTimeout(TimeValue.timeValueSeconds(5));

        if (StringUtils.isBlank(queryField)) {
            MatchAllQueryBuilder matchAllQueryBuilder = QueryBuilders.matchAllQuery();
            deleteRequest.setQuery(matchAllQueryBuilder);
        } else {
            MatchQueryBuilder matchQueryBuilder = QueryBuilders.matchQuery(queryField, queryContent);
            deleteRequest.setQuery(matchQueryBuilder);
        }

        BulkByScrollResponse bulkByScrollResponse = restHighLevelClient.deleteByQuery(deleteRequest, RequestOptions.DEFAULT);

        return bulkByScrollResponse.getStatus();
    }

    /**
     * 批量同步 -> Elasticsearch
     *
     * @param startIndex
     * @param endIndex
     * @param contents
     * @param indexName
     * @return
     * @throws Exception
     */
    private boolean esLoadData(int startIndex, int endIndex, String contents, String indexName) throws Exception {
        if (StringUtils.isBlank(contents) || contents.length() < 4) {
            throw new RuntimeException("es 存储内容不能少于四个字");
        }

        long start = System.currentTimeMillis();
        int size = contents.length() - 4;

        BulkRequest bulkRequest = new BulkRequest();
        bulkRequest.timeout(TimeValue.timeValueSeconds(10));
        int count = 0;
        for (int i = startIndex; i < endIndex; i++, count++) {
            ReportObstacleDto reportObstacle = new ReportObstacleDto();
            reportObstacle.setId(System.currentTimeMillis());
            reportObstacle.setObstacleNo(System.currentTimeMillis());
            int between = RandomUtils.getBetween(0, size);
            reportObstacle.setObstacleTitle(UUID.randomUUID().toString() + contents.substring(between, between + 4));
            reportObstacle.setObstacleDesc(contents.substring(between, between + 4));
            reportObstacle.setObstacleTime(System.currentTimeMillis());
            between = between < 4 ? 4 : between;
            reportObstacle.setSystemName(contents.substring(between - 4, between));
            between = between >= size ? size - 2 : between;
            reportObstacle.setModuleName(between >= size * 0.5 ? contents.substring(between - 2, between) : "");

            bulkRequest.add(new IndexRequest(indexName)
                    .id("" + (i + 1))
                    .source(Objects.requireNonNull(JsonUtils.toJson(reportObstacle)), XContentType.JSON));
        }

        BulkResponse bulk = restHighLevelClient.bulk(bulkRequest, RequestOptions.DEFAULT);
        boolean createResult = !bulk.hasFailures();

        long end = System.currentTimeMillis();

        log.info(String.format("ES在索引: %s, 目标生成: %s 条数据, 是否成功：%s, 耗时: %s秒",
                indexName, count, createResult, (end - start) / 1000));
        return createResult;
    }


}
