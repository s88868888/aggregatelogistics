package net.kdks.handler;

import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson.JSON;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kdks.config.ShunfengConfig;
import net.kdks.constant.CommonConstant;
import net.kdks.enums.ExpressCompanyCodeEnum;
import net.kdks.enums.ExpressStateEnum;
import net.kdks.model.CreateOrderParam;
import net.kdks.model.ExpressData;
import net.kdks.model.ExpressParam;
import net.kdks.model.ExpressPriceParam;
import net.kdks.model.ExpressPriceResult;
import net.kdks.model.ExpressResponse;
import net.kdks.model.ExpressResult;
import net.kdks.model.OrderResult;
import net.kdks.model.sf.Route;
import net.kdks.model.sf.RouteResps;
import net.kdks.model.sf.ShunfengResult;
import net.kdks.model.sf.WaybillNoInfo;
import net.kdks.utils.DateUtils;
import net.kdks.utils.DigestUtils;
import net.kdks.utils.MapUtils;
import net.kdks.utils.StringUtils;

/**
 * 顺丰.
 *
 * @author Ze.Wang
 * @since 0.0.1
 */
public class ExpressShunfengHandler implements ExpressHandler {

    private ShunfengConfig shunfengConfig;
    private static final String SUCCESS_FLAG = "A1000";

    public ExpressShunfengHandler(ShunfengConfig shunfengConfig) {
        this.shunfengConfig = shunfengConfig;
    }

    /**
     * 查询轨迹信息.
     *
     * @param expressParam 快递号、手机、快递公司编码
     * @return 查询接口
     */
    @Override
    public ExpressResponse<List<ExpressResult>> getExpressInfo(ExpressParam expressParam) {
        String mobile = expressParam.getMobile().trim();
        if (StringUtils.isEmpty(mobile) || mobile.length() < 4) {
            return ExpressResponse.failed("请输入手机号后四位");
        }
        mobile = mobile.substring(mobile.length() - 4);
        String requestUrl = getRequestUrl();
        String serviceCode = "EXP_RECE_SEARCH_ROUTES";
        Map<String, Object> paramItemsMap = new HashMap<>(5);
        List<String> expressNos = expressParam.getExpressNos();
        paramItemsMap.put("checkPhoneNo", mobile);
        paramItemsMap.put("methodType", "1");
        paramItemsMap.put("trackingType", "1");
        paramItemsMap.put("language", "0");
        paramItemsMap.put("trackingNumber", expressNos);
        Map<String, Object> paramMap = getBaseParam(serviceCode, paramItemsMap);

        String responseData = HttpRequest.post(requestUrl)
            .header(Header.CONTENT_TYPE, "application/x-www-form-urlencoded")
            .form(paramMap)
            .execute().body();
        return disposeResult(responseData, expressParam);

    }

    /**
     * 结果处理.
     *
     * @param responseData 响应
     * @return 处理结果
     */
    private ExpressResponse<List<ExpressResult>> disposeResult(String responseData,
                                                               ExpressParam expressParam) {
        List<String> expressNos = expressParam.getExpressNos();
        ShunfengResult result = JSON.parseObject(responseData, ShunfengResult.class);
        List<ExpressResult> expressResults = new ArrayList<>();

        if (SUCCESS_FLAG.equals(result.getApiResultCode())) {
            if (result.getApiResultData().getSuccess()) {
                List<RouteResps> routeResps =
                    result.getApiResultData().getMsgData().getRouteResps();
                if (routeResps == null || routeResps.size() == 0) {
                    return ExpressResponse.failed(CommonConstant.NO_INFO);
                }
                Map<String, RouteResps> routeRespsMap = MapUtils.newHashMap(routeResps.size());
                for (RouteResps resps : routeResps) {
                    routeRespsMap.put(resps.getMailNo(), resps);
                }
                for (String expressNo : expressNos) {
                    ExpressResult expressResult =
                        disposeRoute(routeRespsMap.get(expressNo), expressParam, responseData);
                    expressResults.add(expressResult);
                }
                return ExpressResponse.ok(expressResults);
            } else {
                return ExpressResponse.failed(result.getApiResultData().getErrorMsg());
            }
        }
        return ExpressResponse.failed(result.getApiErrorMsg());
    }

    /**
     * 路由处理.
     *
     * @param routeResps 路由信息
     * @param expressParam 快递参数
     * @param responseData 响应
     * @return 处理结果
     */
    private ExpressResult disposeRoute(RouteResps routeResps, ExpressParam expressParam,
                                       String responseData) {
        ExpressResult expressResult = new ExpressResult();
        if (expressParam.isViewOriginal()) {
            expressResult.setOriginalResult(responseData);
        }
        expressResult.setCom(ExpressCompanyCodeEnum.SF.getValue());
        expressResult.setNu(routeResps.getMailNo());
        List<Route> routes = routeResps.getRoutes();
        if (routes == null || routes.size() == 0) {
            expressResult.setState(ExpressStateEnum.NO_INFO.getValue());
            expressResult.setMsg(CommonConstant.NO_INFO);
            return expressResult;
        }
        //默认正序，改为倒序
        Collections.reverse(routes);
        ExpressData latestData = routes.get(0);
        if (expressParam.isViewRoute()) {
            List<ExpressData> data = new ArrayList<>(routes.size());
            data.addAll(routes);
            expressResult.setData(data);
        }
        expressResult.setState(latestData.getStatus());
        if (ExpressStateEnum.SIGNED.getValue().equals(expressResult.getState())) {
            expressResult.setIscheck(CommonConstant.YES);
        }
        return expressResult;
    }

    /**
     * 运费预估.
     *
     * @param expressPriceParam 起始省份、起始城市、目的省份、目的城市、重量、长、宽、高
     * @return 运费
     */
    @Override
    public ExpressResponse<ExpressPriceResult> getExpressPrice(
        ExpressPriceParam expressPriceParam) {
        String serviceCode = "EXP_RECE_QUERY_SFPRICE";

        Map<String, Object> paramItemsMap = new HashMap<>(8);
        paramItemsMap.put("searchPrice", "1");
        paramItemsMap.put("expectedTime", "");

        // 寄件地址
        Map<String, Object> srcAddress = new HashMap<>(4);
        srcAddress.put("province", expressPriceParam.getStartProvince());
        srcAddress.put("city", expressPriceParam.getStartCity());
        srcAddress.put("district", expressPriceParam.getStartDistrict() != null ? expressPriceParam.getStartDistrict() : "");
        srcAddress.put("address", expressPriceParam.getStartAddress() != null ? expressPriceParam.getStartAddress() : "");
        paramItemsMap.put("srcAddress", srcAddress);

        // 收件地址
        Map<String, Object> destAddress = new HashMap<>(4);
        destAddress.put("province", expressPriceParam.getEndProvince());
        destAddress.put("city", expressPriceParam.getEndCity());
        destAddress.put("district", expressPriceParam.getEndDistrict() != null ? expressPriceParam.getEndDistrict() : "");
        destAddress.put("address", expressPriceParam.getEndAddress() != null ? expressPriceParam.getEndAddress() : "");
        paramItemsMap.put("destAddress", destAddress);

        // 货物信息
        Map<String, Object> cargoDetails = new HashMap<>(4);
        cargoDetails.put("weight", expressPriceParam.getWeight() != null ? expressPriceParam.getWeight() : 1);
        if (expressPriceParam.getLength() != null) cargoDetails.put("length", expressPriceParam.getLength());
        if (expressPriceParam.getWidth() != null) cargoDetails.put("width", expressPriceParam.getWidth());
        if (expressPriceParam.getHeight() != null) cargoDetails.put("height", expressPriceParam.getHeight());
        paramItemsMap.put("cargoDetails", cargoDetails);

        Map<String, Object> paramMap = getBaseParam(serviceCode, paramItemsMap);
        String responseData = HttpRequest.post(getRequestUrl())
            .header(Header.CONTENT_TYPE, "application/x-www-form-urlencoded")
            .form(paramMap)
            .execute().body();

        return disposePriceResult(responseData);
    }

    private ExpressResponse<ExpressPriceResult> disposePriceResult(String responseData) {
        ShunfengResult result = JSON.parseObject(responseData, ShunfengResult.class);
        if (SUCCESS_FLAG.equals(result.getApiResultCode())) {
            if (result.getApiResultData().getSuccess()) {
                // 解析价格字段
                String msgDataStr = JSON.toJSONString(result.getApiResultData().getMsgData());
                com.alibaba.fastjson.JSONObject msgData = JSON.parseObject(msgDataStr);
                if (msgData != null) {
                    ExpressPriceResult priceResult = new ExpressPriceResult();
                    // 顺丰返回 totalPrice 或 price 字段
                    java.math.BigDecimal price = msgData.getBigDecimal("totalPrice");
                    if (price == null) price = msgData.getBigDecimal("price");
                    if (price == null) price = msgData.getBigDecimal("freight");
                    priceResult.setPrice(price);
                    priceResult.setTime(msgData.getBigDecimal("deliveryTime"));
                    return ExpressResponse.ok(priceResult);
                }
            } else {
                return ExpressResponse.failed(result.getApiResultData().getErrorMsg());
            }
        }
        return ExpressResponse.failed(result.getApiErrorMsg());
    }

    /**
     * 清单运费查询（EXP_RECE_QUERY_SFWAYBILL）.
     * 通过运单号查询实际运费明细。
     *
     * @param trackingNo 运单号
     * @return 运费信息（waybillInfo + waybillFeeList）
     */
    public ExpressResponse<com.alibaba.fastjson.JSONObject> queryWaybillFee(String trackingNo) {
        String serviceCode = "EXP_RECE_QUERY_SFWAYBILL";
        Map<String, Object> paramItemsMap = new HashMap<>(3);
        paramItemsMap.put("trackingType", "2");
        paramItemsMap.put("trackingNum", trackingNo);

        Map<String, Object> paramMap = getBaseParam(serviceCode, paramItemsMap);
        String responseData = HttpRequest.post(getRequestUrl())
            .header(Header.CONTENT_TYPE, "application/x-www-form-urlencoded")
            .form(paramMap)
            .execute().body();

        return disposeWaybillFeeResult(responseData);
    }

    private ExpressResponse<com.alibaba.fastjson.JSONObject> disposeWaybillFeeResult(String responseData) {
        com.alibaba.fastjson.JSONObject json = JSON.parseObject(responseData);
        if (json == null) {
            return ExpressResponse.failed("响应为空");
        }
        // 清单运费查询接口直接返回 {success, errorCode, errorMsg, msgData}
        // 不走 apiResultCode/apiResultData 的嵌套结构
        Boolean success = json.getBoolean("success");
        if (Boolean.TRUE.equals(success)) {
            Object msgDataObj = json.get("msgData");
            com.alibaba.fastjson.JSONObject msgData = null;
            if (msgDataObj instanceof com.alibaba.fastjson.JSONObject) {
                msgData = (com.alibaba.fastjson.JSONObject) msgDataObj;
            } else if (msgDataObj instanceof String) {
                msgData = JSON.parseObject((String) msgDataObj);
            }
            if (msgData != null) {
                return ExpressResponse.ok(msgData);
            }
            return ExpressResponse.failed("msgData 为空");
        }
        // 可能走旧格式 apiResultCode
        String apiResultCode = json.getString("apiResultCode");
        if (SUCCESS_FLAG.equals(apiResultCode)) {
            String apiResultDataStr = json.getString("apiResultData");
            com.alibaba.fastjson.JSONObject apiResultData = JSON.parseObject(apiResultDataStr);
            if (apiResultData != null && Boolean.TRUE.equals(apiResultData.getBoolean("success"))) {
                Object msgDataObj = apiResultData.get("msgData");
                com.alibaba.fastjson.JSONObject msgData = null;
                if (msgDataObj instanceof com.alibaba.fastjson.JSONObject) {
                    msgData = (com.alibaba.fastjson.JSONObject) msgDataObj;
                } else if (msgDataObj instanceof String) {
                    msgData = JSON.parseObject((String) msgDataObj);
                }
                if (msgData != null) {
                    return ExpressResponse.ok(msgData);
                }
            }
            String errorMsg = apiResultData != null ? apiResultData.getString("errorMessage") : null;
            return ExpressResponse.failed(errorMsg != null ? errorMsg : "查询失败");
        }
        String errorMsg = json.getString("errorMsg");
        if (errorMsg == null) errorMsg = json.getString("apiErrorMsg");
        return ExpressResponse.failed(errorMsg != null ? errorMsg : "查询失败");
    }

    /**
     * 派件通知查询（EXP_RECE_DELIVERY_NOTICE）.
     * 查询派件员信息及预计送达时间。
     *
     * @param trackingNo 运单号
     * @return 派件通知信息（派件员姓名、电话、预计送达时间等）
     */
    public ExpressResponse<com.alibaba.fastjson.JSONObject> queryDeliveryNotice(String trackingNo) {
        String serviceCode = "EXP_RECE_DELIVERY_NOTICE";
        Map<String, Object> paramItemsMap = new HashMap<>(3);
        paramItemsMap.put("trackingType", "2");
        paramItemsMap.put("trackingNumber", Collections.singletonList(trackingNo));

        Map<String, Object> paramMap = getBaseParam(serviceCode, paramItemsMap);
        String responseData = HttpRequest.post(getRequestUrl())
            .header(Header.CONTENT_TYPE, "application/x-www-form-urlencoded")
            .form(paramMap)
            .execute().body();

        return disposeDeliveryNoticeResult(responseData);
    }

    private ExpressResponse<com.alibaba.fastjson.JSONObject> disposeDeliveryNoticeResult(String responseData) {
        com.alibaba.fastjson.JSONObject json = JSON.parseObject(responseData);
        if (json == null) {
            return ExpressResponse.failed("响应为空");
        }
        // 尝试标准格式 apiResultCode
        String apiResultCode = json.getString("apiResultCode");
        if (SUCCESS_FLAG.equals(apiResultCode)) {
            String apiResultDataStr = json.getString("apiResultData");
            com.alibaba.fastjson.JSONObject apiResultData = null;
            if (apiResultDataStr != null) {
                apiResultData = JSON.parseObject(apiResultDataStr);
            }
            if (apiResultData != null && Boolean.TRUE.equals(apiResultData.getBoolean("success"))) {
                Object msgDataObj = apiResultData.get("msgData");
                com.alibaba.fastjson.JSONObject msgData = null;
                if (msgDataObj instanceof com.alibaba.fastjson.JSONObject) {
                    msgData = (com.alibaba.fastjson.JSONObject) msgDataObj;
                } else if (msgDataObj instanceof String) {
                    msgData = JSON.parseObject((String) msgDataObj);
                }
                if (msgData != null) {
                    return ExpressResponse.ok(msgData);
                }
            }
            String errorMsg = apiResultData != null ? apiResultData.getString("errorMsg") : null;
            if (errorMsg == null && apiResultData != null) errorMsg = apiResultData.getString("errorMessage");
            return ExpressResponse.failed(errorMsg != null ? errorMsg : "查询失败");
        }
        // 尝试直接格式 {success, msgData}
        Boolean success = json.getBoolean("success");
        if (Boolean.TRUE.equals(success)) {
            Object msgDataObj = json.get("msgData");
            com.alibaba.fastjson.JSONObject msgData = null;
            if (msgDataObj instanceof com.alibaba.fastjson.JSONObject) {
                msgData = (com.alibaba.fastjson.JSONObject) msgDataObj;
            } else if (msgDataObj instanceof String) {
                msgData = JSON.parseObject((String) msgDataObj);
            }
            if (msgData != null) {
                return ExpressResponse.ok(msgData);
            }
        }
        String errorMsg = json.getString("apiErrorMsg");
        if (errorMsg == null) errorMsg = json.getString("errorMsg");
        return ExpressResponse.failed(errorMsg != null ? errorMsg : "查询失败");
    }

    /**
     * 创建订单.
     *
     * @param createOrderParam 下单参数，主要包含物品信息、收件人信息、寄件人信息等
     * @return 快递单号等信息
     */
    @Override
    public ExpressResponse<OrderResult> createOrder(CreateOrderParam createOrderParam) {


        Map<String, Object> cargoDetailsMap =
            JSON.parseObject(JSON.toJSONString(createOrderParam.getCargoDetail()), HashMap.class);
        cargoDetailsMap.put("sourceArea", "CHN");

        List<Map<String, Object>> cargoDetailsList = new ArrayList<>();
        cargoDetailsList.add(cargoDetailsMap);

        Map<String, Object> contactInfoSendMap =
            JSON.parseObject(JSON.toJSONString(createOrderParam.getSendContactInfo()),
                HashMap.class);
        contactInfoSendMap.put("contactType", 1);
        contactInfoSendMap.put("country", "CN");
        Map<String, Object> contactInfoReceiptMap =
            JSON.parseObject(JSON.toJSONString(createOrderParam.getReceiptContactInfo()),
                HashMap.class);
        contactInfoReceiptMap.put("contactType", 2);
        contactInfoReceiptMap.put("country", "CN");
        List<Map<String, Object>> contactInfoList = new ArrayList<>();
        contactInfoList.add(contactInfoSendMap);
        contactInfoList.add(contactInfoReceiptMap);

        Map<String, Object> paramItemsMap = new HashMap<>(4);
        paramItemsMap.put("language", "zh-CN");
        paramItemsMap.put("orderId", createOrderParam.getOrderId());
        paramItemsMap.put("cargoDetails", cargoDetailsList);
        paramItemsMap.put("contactInfoList", contactInfoList);
        Map<String, Object> paramMap = getBaseParam("EXP_RECE_CREATE_ORDER", paramItemsMap);

        String responseData = HttpRequest.post(getRequestUrl())
            .header(Header.CONTENT_TYPE, "application/x-www-form-urlencoded")
            .form(paramMap)
            .execute().body();

        return disposeCreateOrderResult(responseData, createOrderParam.getOrderId());
    }

    /**
     * 结果处理.
     *
     * @param responseData 响应
     * @return 处理结果
     */
    private ExpressResponse<OrderResult> disposeCreateOrderResult(String responseData,
                                                                  String orderId) {
        OrderResult orderResult = new OrderResult();
        orderResult.setOrderId(orderId);
        ShunfengResult result = JSON.parseObject(responseData, ShunfengResult.class);
        String successFlag = "A1000";
        if (successFlag.equals(result.getApiResultCode())) {
            if (result.getApiResultData().getSuccess()) {
                List<WaybillNoInfo> waybillNoInfoList =
                    result.getApiResultData().getMsgData().getWaybillNoInfoList();
                if (waybillNoInfoList != null && waybillNoInfoList.size() != 0) {
                    orderResult.setExpressNo(waybillNoInfoList.get(0).getWaybillNo());
                    return ExpressResponse.ok(orderResult);
                }
            } else {
                return ExpressResponse.failed(result.getApiResultData().getErrorMsg());
            }
        }
        return ExpressResponse.failed(result.getApiErrorMsg());
    }

    private String getRequestUrl() {
        String requestUrl = "https://sfapi.sf-express.com/std/service";
        if (shunfengConfig.getIsProduct() == 0) {
            requestUrl = "https://sfapi-sbox.sf-express.com/std/service";
        }
        return requestUrl;
    }

    private Map<String, Object> getBaseParam(String serviceCode,
                                             Map<String, Object> paramItemsMap) {
        Map<String, Object> paramMap = new HashMap<>(6);
        String partnerId = shunfengConfig.getPartnerId();
        // requestId 每次请求生成唯一UUID，若配置中有值则使用配置值
        String requestId = shunfengConfig.getRequestId();
        if (StringUtils.isEmpty(requestId)) {
            requestId = java.util.UUID.randomUUID().toString();
        }
        Long timestamp = DateUtils.currentTimeMillis();
        String msgDigest = null;
        paramMap.put("partnerID", partnerId);
        paramMap.put("requestID", requestId);
        paramMap.put("serviceCode", serviceCode);
        paramMap.put("timestamp", timestamp);
        String param = JSON.toJSONString(paramItemsMap);
        paramMap.put("msgData", param);

        StringBuilder beforeDigestStr = new StringBuilder(param);
        beforeDigestStr.append(timestamp).append(shunfengConfig.getCheckWord());
        msgDigest = Base64.getEncoder().encodeToString(DigestUtils.md5Digest(beforeDigestStr.toString()));
        paramMap.put("msgDigest", msgDigest);
        return paramMap;
    }

    /**
     * 获取当前快递公司编码.
     *
     * @return 快递公司编码
     */
    @Override
    public String getExpressCompanyCode() {
        return ExpressCompanyCodeEnum.SF.getValue();
    }

}

