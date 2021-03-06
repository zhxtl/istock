package io.github.kingschan1204.istock.module.maindata.services;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.result.UpdateResult;
import io.github.kingschan1204.istock.common.db.MyMongoTemplate;
import io.github.kingschan1204.istock.common.util.stock.StockSpider;
import io.github.kingschan1204.istock.common.util.stock.impl.JisiluSpilder;
import io.github.kingschan1204.istock.module.maindata.po.CsIndexIndustry;
import io.github.kingschan1204.istock.module.maindata.po.Stock;
import io.github.kingschan1204.istock.module.maindata.po.StockDividend;
import io.github.kingschan1204.istock.module.maindata.po.StockYearReport;
import io.github.kingschan1204.istock.module.maindata.repository.StockHisDividendRepository;
import io.github.kingschan1204.istock.module.maindata.repository.StockRepository;
import io.github.kingschan1204.istock.module.maindata.vo.StockVo;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.mapreduce.MapReduceOptions;
import org.springframework.data.mongodb.core.mapreduce.MapReduceResults;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Stock service
 *
 * @author chenguoxiang
 * @create 2018-03-27 10:27
 **/
@Service
public class StockService {
    @Autowired
    private StockRepository stockRepository;
    @Autowired
    private StockHisDividendRepository stockHisDividendRepository;
    @Autowired
    private StockYearReportService stockYearReportService;
    @Autowired
    private StockSpider spider;
    @Autowired
    private MongoTemplate template;
    @Autowired
    private MyMongoTemplate myMongoTemplate;
    @Autowired
    private JisiluSpilder jisiluSpilder;


    /**
     * ????????????
     *
     * @param pageindex  ??????
     * @param pagesize   ???????????????
     * @param pcode      ???????????????
     * @param type       ??????
     * @param pb         ?????????
     * @param dy         ??????
     * @param orderfidld ????????????
     * @param psort      ????????????
     * @return
     */
    public String queryStock(int pageindex, int pagesize, final String pcode, final String type, String pb, String dy, String industry, String orderfidld, String psort) {
        Document dbObject = new Document();
        Document fieldObject = new Document();
        fieldObject.put("todayMax", false);
        fieldObject.put("todayMin", false);
        fieldObject.put("priceDate", false);
        fieldObject.put("mainBusiness", false);
        fieldObject.put("dyDate", false);
        fieldObject.put("infoDate", false);
        fieldObject.put("dividendUpdateDay", false);
        Query query = new BasicQuery(dbObject, fieldObject);
        //Projections.exclude(Arrays.asList("todayMax","todayMin","priceDate","mainBusiness","dyDate","infoDate","dividendUpdateDay"));

        Optional<String> code = Optional.ofNullable(pcode);
        if (code.isPresent()) {
            if (pcode.matches("\\d{6}")) {
                query.addCriteria(Criteria.where("_id").is(pcode));
            }else if (pcode.contains(",")){
                query.addCriteria(Criteria.where("_id").in(pcode.split(",")));
            }
            else if (pcode.matches("\\d+")) {
                query.addCriteria(Criteria.where("_id").regex(pcode));
            } else {
                query.addCriteria(Criteria.where("name").regex(pcode));
            }
        }
        if (null != type && type.matches("sz|sh")) {
            query.addCriteria(Criteria.where("type").is(type));
        }
        if (null != pb && pb.matches("\\d+(\\.\\d+)?\\-\\d+(\\.\\d+)?")) {
            double s = Double.parseDouble(pb.split("-")[0]);
            double d = Double.parseDouble(pb.split("-")[1]);
            query.addCriteria(Criteria.where("pb").gte(s).lte(d));
        }
        if (null != dy && dy.matches("(dy|dividend|fiveYearDy)\\-\\d+(\\.\\d+)?\\-\\d+(\\.\\d+)?")) {
            String field = dy.split("-")[0];
            double s = Double.parseDouble(dy.split("-")[1]);
            double d = Double.parseDouble(dy.split("-")[2]);
            query.addCriteria(Criteria.where(field).gte(s).lte(d));
        }
        if (null != industry && !industry.isEmpty()) {
            int i_index=industry.lastIndexOf("-");
            String type_key=industry.replaceAll("\\|\\-+|\\s","");
            String query_field=null;
            if(i_index==-1){
                query_field="lvone";
            }else if(i_index==2){
                query_field="lvtwo";
            }else if(i_index==3){
                query_field="lvthree";
            }else {
                query_field="lvfour";
            }
            List<String> code_list=new ArrayList<>();
            List<CsIndexIndustry> inlist= template.find(Query.query(Criteria.where(query_field).is(type_key)), CsIndexIndustry.class);
            for (CsIndexIndustry c: inlist) {
                code_list.add(c.getCode());
            }
            query.addCriteria(Criteria.where("_id").in(code_list));
        }
        //????????????
        Long total = template.count(query, Stock.class);
        //??????
        query.skip((pageindex - 1) * pagesize).limit(pagesize);
        //??????
        Sort.Direction sortd = "asc".equalsIgnoreCase(psort) ? Sort.Direction.ASC : Sort.Direction.DESC;
        query.with(Sort.by(sortd, orderfidld));
        //code
        List<Stock> list = template.find(query, Stock.class);
        //???????????????vo???????????????????????????
        List<StockVo> temp = JSON.parseArray(JSON.toJSONString(list), StockVo.class);
        JSONObject data = new JSONObject();
        long pagetotal = total % pagesize == 0 ? total / pagesize : total / pagesize + 1;
        data.put("rows", JSONArray.parseArray(JSON.toJSONString(temp)));
        //????????????
        data.put("total", pagetotal);
        // ????????????????????????
        data.put("records", total);
        data.put("page", pageindex);
        return data.toJSONString();
    }


    /**
     * ??????????????????????????????
     *
     * @param code
     * @return
     */
    public List<StockDividend> getStockDividend(String code) {
        Query query = new Query();
        query.addCriteria(Criteria.where("code").is(code));
        //??????
        query.with(Sort.by(Sort.Direction.ASC, "title"));
        //code
        List<StockDividend> list = template.find(query, StockDividend.class);
        return list;
    }


    /**
     * ??????????????????roe
     *
     * @param code
     * @return
     */
    public List<StockYearReport> getStockHisRoe(String code) {
        Query query = new Query();
        query.addCriteria(Criteria.where("code").is(code));
        //??????
        query.with(Sort.by(Sort.Direction.ASC, "year"));
        //code
        List<StockYearReport> list = template.find(query, StockYearReport.class);
        return list;
    }


    /**
     * ??????????????????
     *
     * @param code
     * @return
     * @throws Exception
     */
    public List<String> crawAndSaveHisPbPe(String code) throws Exception {

        StringBuilder price = new StringBuilder();
        StringBuilder pe = new StringBuilder();
        StringBuilder pb = new StringBuilder();
        StringBuilder date = new StringBuilder();

        JSONObject data = jisiluSpilder.crawHisPbPePriceAndReports(code);
        List<Document> list = new ArrayList<Document>();
//        MongoCollection<Document> hisdata = template.getCollection("stock_his_pe_pb");
        MongoCollection<Document> report = template.getCollection("stock_report");

       /* JSONArray hisdataJsons = data.getJSONArray("hisdata");
        for (int i = 0; i < hisdataJsons.size(); i++) {
            JSONObject row = hisdataJsons.getJSONObject(i);
            Document object = new Document();
            object.put("code", code);
            object.put("date", row.getString("date"));
            object.put("pb", row.getDouble("pb"));
            object.put("pe", row.getDouble("pe"));
            object.put("price", row.getDouble("price"));
            //?????????????????????
            date.append("'").append(row.getString("date")).append("'");
            price.append(row.getDouble("price"));
            pb.append(row.getDouble("pb"));
            pe.append(row.getDouble("pe"));
            if (i != hisdataJsons.size() - 1) {
                price.append(",");
                pb.append(",");
                pe.append(",");
                date.append(",");
            }

            list.add(object);
            if ((i != 0 && i % 1000 == 0) || i == hisdataJsons.size() - 1) {
                hisdata.insertMany(list);
                list.clear();
            }
        }*/


        JSONArray reportJsons = data.getJSONArray("reports");
        list = new ArrayList<>();
        for (int i = 0; i < reportJsons.size(); i++) {
            JSONObject row = reportJsons.getJSONObject(i);
            Document object = new Document();
            object.put("code", code);
            object.put("releaseDay", row.getString("releaseDay"));
            object.put("link", row.getString("link"));
            object.put("title", row.getString("title"));
            list.add(object);
            if ((i != 0 && i % 1000 == 0) || i == reportJsons.size() - 1) {
                report.insertMany(list);
                list.clear();
            }
        }
        //??????????????????pb???pe
        List<String> result = new ArrayList<>();
        result.add(price.toString());
        result.add(date.toString());
        result.add(pb.toString());
        result.add(pe.toString());
        result.add(reportJsons.toJSONString());
        return result;
    }


    /**
     * db.getCollection('stock').update({},{$set : {"fiveYearDy":0}},false,true)
     * ????????????5????????????????????????????????????
     * @param startYear ????????????????????????
     * @param endYear ??????????????????
     * @param key ?????????????????????????????????
     */
    public void calculateFiveYearsDy(int startYear, int endYear,String key) {
        Query query = new Query();
        query.addCriteria(Criteria.where("title").gte(String.valueOf(startYear)));
        query.with(Sort.by(
                Sort.Order.asc("code"),
                Sort.Order.desc("title")
        ));
        MapReduceResults<Document> result = template.mapReduce(query, "stock_dividend",
                "classpath:/mapreduce/5years_dy/dy5years_map.js", "classpath:/mapreduce/5years_dy/dy5years_reduce.js",
                new MapReduceOptions().outputCollection("stock_dy_statistics"), Document.class);

        Iterator<Document> iter = result.iterator();
        while (iter.hasNext()) {
            Document item = iter.next();
            String code = item.getString("_id");
            Document value = (Document) item.get("value");
            if (value.containsKey("key") && value.getDouble("key") > (endYear-startYear-1)) {
                double percent = Double.parseDouble(value.getString("percent"));
                UpdateResult updateResult = template.upsert(
                        new Query(Criteria.where("_id").is(code)),
                        new Update()
                                .set("_id", code)
                                .set(key, percent),
                        "stock"
                );
            }

        }
    }

    /**
     * ???????????????5????????????roe
     *
     * @param startYear ????????????
     * @param endYear   ????????????
     * @param key ?????????????????????????????????
     */
    public void calculateFiveYearsRoe(int startYear, int endYear,String key) {
        Query query = new Query();
        query.addCriteria(Criteria.where("year").gte(startYear));
        MapReduceResults<Document> result = template.mapReduce(query, "stock_year_report",
                "classpath:/mapreduce/5years_roe/map.js", "classpath:/mapreduce/5years_roe/reduce.js",
                new MapReduceOptions().outputCollection("stock_hisroe_statistics"), Document.class);
        Iterator<Document> iter = result.iterator();
        while (iter.hasNext()) {
            Document item = iter.next();
            String code = item.getString("_id");
            Document value = (Document) item.get("value");
            if (value.containsKey("size") && value.getDouble("size") > (endYear-startYear-1)) {
                double percent = value.getDouble("percent");
                template.upsert(
                        new Query(Criteria.where("_id").is(code)),
                        new Update()
                                .set("_id", code)
                                .set(key, percent),
                        "stock"
                );
            }

        }
    }

    /**
     * ??????????????????
     *
     * @return
     */
    public List<String> getAllIntruduce() {
        AggregationResults<Document> a = template.aggregate(
                Aggregation.newAggregation(
                        Aggregation.group("industry").count().as("count"))
                , Stock.class, Document.class);
        List<String> list = new ArrayList<String>();
        if (null != a.getMappedResults()) {
            for (Document doc : a.getMappedResults()) {
                String key = doc.getString("_id");
                if (null==key||key.isEmpty()) {
                    continue;
                }
                list.add(key);
            }
        }
        return list;
    }

}
