package com.hik.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hik.dao.FaceAttendanceDao;
import com.hik.entity.FaceAttendance;
import com.hik.enums.ResultEnum;
import com.hik.exception.PayException;
import com.hik.util.DateUtil;
import com.hik.util.HttpUtil;
import com.hik.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.httpclient.NameValuePair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class FaceAttendanceService {


    @Autowired
    private FaceAttendanceDao faceAttendanceDao;


//    private final static String ADDRESS = "http://192.168.30.200";

    private final static String ADDRESS = "http://218.21.36.18:8200";
    private final static String SUFFIX = "/person/record/query/time";


    /**
     * 调取人脸数据
     *
     * @param startTime
     * @param endTime
     */
    public void call(String startTime, String endTime) {
        int pageIndex = 0;
        int pageSize = 100;
        String upload = "0";
        JSONObject resp = queryWithTime(startTime, endTime, String.valueOf(pageIndex), String.valueOf(pageSize), upload);
        boolean result = resp.getBoolean("success");
        if (!result) {
            log.error("【调用人脸考勤出入记录按起止时间查询接口获取数据失败】startTime = {} , endTime = {},resp = {}", startTime, endTime, resp);
            throw new PayException(ResultEnum.CALL_FACE_INTERFACE_FAIL);
        }
        //总共数量
        int totalCount = resp.getInteger("result");
        JSONArray jsonArray = resp.getJSONArray("data");
        List<FaceAttendance> faceAttendanceList = new ArrayList<>();
        //查询出来的数量
        int queryCount = jsonArray.size();
        if (totalCount > queryCount) {
            //剩余的数量
            int residueCount = totalCount - queryCount;
            //待循环的次数
            int loopTimes = ((int) (residueCount / pageSize)) + 1;
            log.info("【调用人脸考勤出入记录按起止时间查询接口待循环的次数】 loopTimes = {},totalCount = {},queryCount = {}", loopTimes, totalCount, queryCount);
            for (int i = 1; i <= loopTimes; i++) {
                JSONObject jo = queryWithTime(startTime, endTime, String.valueOf(i), String.valueOf(pageSize), upload);
                JSONArray ja = jo.getJSONArray("data");
                List<FaceAttendance> list = analysisResp(ja);
                faceAttendanceList.addAll(list);
            }
        } else {
            faceAttendanceList = analysisResp(jsonArray);
        }


        if (faceAttendanceList.size() > 0) {
            log.info("【调用人脸考勤出入记录按起止时间查询接口后待保存数据库集合】size = {}, list = {}", faceAttendanceList.size(), JsonUtil.toJson(faceAttendanceList));
            save(faceAttendanceList);
        }

        log.info("【调用人脸考勤出入记录按起止时间查询接口成功结束】thread = {} ,time = {}", Thread.currentThread(), LocalDateTime.now());

    }

    /**
     * 解析组装返回值
     *
     * @param respData
     */
    public List<FaceAttendance> analysisResp(JSONArray respData) {
        List<FaceAttendance> list = new ArrayList<>();
        for (int i = 0; i < respData.size(); i++) {
            JSONObject faceJSON = respData.getJSONObject(i);
            String jsonStr = JSONObject.toJSONString(faceJSON);
            FaceAttendance faceAttendance = JSONObject.parseObject(jsonStr, FaceAttendance.class);
            faceAttendance.setId(null);
            list.add(faceAttendance);
        }
        return list;
    }

    /**
     * 取人脸考勤数据
     * 出入记录按起止时间查询
     *
     * @param startTime
     * @param endTime
     */
    public JSONObject queryWithTime(String startTime, String endTime, String pageIndex, String pageSize, String upload) {
        NameValuePair[] params = new NameValuePair[]{
                new NameValuePair("start_time", startTime),
                new NameValuePair("end_time", endTime),
                new NameValuePair("page_index", pageIndex),
                new NameValuePair("page_size", pageSize),
                new NameValuePair("upload", upload)
        };
        Map<String, Object> resp = new HashMap<>();
        try {
            log.info("【调用人脸考勤出入记录按起止时间查询接口前】startTime = {} , endTime = {}  ", startTime, endTime);
            resp = HttpUtil.post(ADDRESS + SUFFIX, params);
            log.info("【调用人脸考勤出入记录按起止时间查询接口后】startTime = {} , endTime = {} ,resp ={}", startTime, endTime, resp);
        } catch (Exception e) {
            log.error("【调用人脸考勤出入记录按起止时间查询接口失败】startTime = {} , endTime = {} ,e={}", startTime, endTime, e);
            throw new PayException(ResultEnum.CALL_FACE_INTERFACE_FAIL);
        }
        Object statusCode = resp.get("statusCode");
        String successStatusCode = "200";
        if (statusCode != null && successStatusCode.equals(String.valueOf(statusCode))) {
            JSONObject responseBody = (JSONObject) resp.get("responseBody");
            log.info("【调用人脸考勤出入记录按起止时间查询接口成功结束】thread = {} ,time = {}", Thread.currentThread(), LocalDateTime.now());
            return responseBody;
        } else {
            log.error("【调用人脸考勤出入记录按起止时间查询接口失败】startTime = {} , endTime = {},resp = {}", startTime, endTime, resp);
            throw new PayException(ResultEnum.CALL_FACE_INTERFACE_FAIL);
        }

    }

    @Transactional
    public void init(String startTime, String endTime) {
        List<FaceAttendance> list = faceAttendanceDao.findByDatetimeBetween(startTime, endTime);
        faceAttendanceDao.delete(list);
        call(startTime, endTime);
    }

    @Transactional
    public void initAll() {
        String startTime = "2020-06-15 00:00:00";
        String endTime = DateUtil.getCurrentDate(DateUtil.FULL_FORMAT);
        faceAttendanceDao.deleteAll();
        call(startTime, endTime);
    }


    private final static String SUFFIX2 = "/person/totalCount";

    /**
     * 获取入库总人数
     *
     * @return
     */
    public int queryTotal() {
        //默认总条数
        int defaultTotalCount = 10000;
        NameValuePair[] params = new NameValuePair[]{};
        try {
            Map<String, Object> resp = HttpUtil.post(ADDRESS + SUFFIX2, params);
            log.info("【调用人脸考勤获取入库总人数接口后】resp ={}", resp);
            Object statusCode = resp.get("statusCode");
            String successStatusCode = "200";
            if (statusCode != null && successStatusCode.equals(String.valueOf(statusCode))) {
                JSONObject jsonObject = (JSONObject) resp.get("responseBody");
                boolean result = jsonObject.getBoolean("success");
                if (result) {
                    int totalCount = jsonObject.getInteger("data");
                    return totalCount;
                } else {
                    log.error("【调用人脸考勤获取入库总人数接口获取数据失败】resp = {}", resp);
                }

            }
        } catch (IOException e) {
            log.error("【调用人脸考勤获取入库总人数接口失败】e = {}", e);
        }
        return defaultTotalCount;

    }

    private final static String SUFFIX3 = "/person/query/part";

    /**
     * 1.5人员分页查询接口
     *
     * @param index
     * @param count
     */
    public List<FaceAttendance> queryByPage(String index, String count) {
        NameValuePair[] params = new NameValuePair[]{
                new NameValuePair("index", index),
                new NameValuePair("count", count)
        };
        Map<String, Object> resp = new HashMap<>();
        try {
            log.info("【调用人脸考勤分页查询接口前】index = {} , count = {}  ", index, count);
            resp = HttpUtil.post(ADDRESS + SUFFIX3, params);
        } catch (Exception e) {
            log.error("【调用人脸考勤分页查询接口失败】index = {} , count = {} ,e={}", index, count, e);
            throw new PayException(ResultEnum.CALL_FACE_INTERFACE_FAIL);
        }
        log.info("【调用人脸考勤分页查询接口前后】index = {} , count = {} ,resp ={}", index, count, resp);
        Object statusCode = resp.get("statusCode");
        String successStatusCode = "200";
        if (statusCode != null && successStatusCode.equals(String.valueOf(statusCode))) {
            JSONObject jsonObject = (JSONObject) resp.get("responseBody");
            boolean result = jsonObject.getBoolean("success");
            if (!result) {
                log.error("【调用人脸考勤分页查询接口获取数据失败】index = {} , count = {},resp = {}", index, count, resp);
                throw new PayException(ResultEnum.CALL_FACE_INTERFACE_FAIL);
            }
            JSONArray jsonArray = jsonObject.getJSONArray("data");
            List<FaceAttendance> list = new ArrayList<>();
            for (int i = 0; i < jsonArray.size(); i++) {
                JSONObject faceJSON = jsonArray.getJSONObject(i);
                String jsonStr = JSONObject.toJSONString(faceJSON);
                FaceAttendance faceAttendance = JSONObject.parseObject(jsonStr, FaceAttendance.class);
//                faceAttendance.setId(null);
                list.add(faceAttendance);
            }
//            if (list.size() > 0) {
//                    log.info("【调用人脸考勤分页查询接口后待保存数据库集合】 list = {}", JsonUtil.toJson(list));
//                    save(list);
//                return list;
//            }
            return list;
        } else {
            log.error("【调用人脸考勤分页查询接口失败】index = {} , count = {},resp = {}", index, count, resp);
            throw new PayException(ResultEnum.CALL_FACE_INTERFACE_FAIL);
        }

    }


    @Transactional
    public void save(List<FaceAttendance> list) {
        faceAttendanceDao.save(list);
    }


    public static void main(String[] args) {
        System.out.println((int) .1);
        System.out.println((int) .6);
        System.out.println((int) 1.1);
        System.out.println((int) 1.6);
        System.out.println(Math.round(0.4));
        Thread thread = new Thread();
//        thread.wait();
    }
}
