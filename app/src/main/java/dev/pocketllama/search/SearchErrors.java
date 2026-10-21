package dev.pocketllama.search;

import org.json.*;

/** Only fixed diagnostic codes are displayed; server tracebacks/URLs never enter errors. */
public final class SearchErrors {
    private SearchErrors(){}
    public static String describe(JSONObject failure){
        return describe(failure,"ru");
    }
    public static JSONArray diagnostics(JSONObject failure){
        JSONArray result=new JSONArray(),raw=failure.optJSONArray("diagnostics");if(raw==null)return result;
        for(int i=0;i<Math.min(8,raw.length());i++){
            JSONObject item=raw.optJSONObject(i);if(item==null||!java.util.Arrays.asList("google","brave","duckduckgo","bing rss","google news rss","bing news rss","wikipedia").contains(item.optString("engine")))continue;
            String reason=item.optString("reason");if(!java.util.Arrays.asList("dns","certificate","tls","captcha","rate_limited","access_denied","timeout","connection","http_error","invalid_response","internal").contains(reason))reason="internal";
            try{result.put(new JSONObject().put("engine",item.getString("engine")).put("reason",reason));}catch(JSONException ignored){}
        }
        return result;
    }
    public static String describe(JSONObject failure,String language){
        boolean en="en".equals(language);
        if("search_internal_error".equals(failure.optString("error")))return en?"SearXNG could not complete the search because of an internal error.":"Внутренняя ошибка SearXNG. Поисковый запрос не выполнен";
        StringBuilder detail=new StringBuilder();JSONArray diagnostics=diagnostics(failure);
        for(int i=0;i<Math.min(6,diagnostics.length());i++){
            JSONObject item=diagnostics.optJSONObject(i);String name=item.optString("engine"),reason=item.optString("reason");
            String label;
            switch(reason){
                case "dns":label=en?"could not resolve server (DNS)":"не удалось определить адрес сервера (DNS)";break;
                case "certificate":label=en?"HTTPS certificate error; check device date and network":"ошибка сертификата HTTPS; проверьте дату телефона и настройки сети";break;
                case "tls":label=en?"HTTPS connection failed":"сбой HTTPS-соединения";break;
                case "captcha":label=en?"CAPTCHA required":"поисковик требует CAPTCHA";break;
                case "rate_limited":label=en?"request rate limited":"поисковик ограничил частоту запросов";break;
                case "access_denied":label=en?"request denied":"поисковик отклонил запрос";break;
                case "timeout":label=en?"request timed out":"истекло время ожидания";break;
                case "connection":label=en?"connection failed":"не удалось подключиться";break;
                case "http_error":label=en?"provider HTTP error":"ошибка ответа поисковика";break;
                case "invalid_response":label=en?"provider returned an invalid response":"поисковик вернул неподходящий ответ";break;
                default:label=en?"internal search error":"внутренняя ошибка поиска";
            }
            if(name.equals("google news rss"))name=en?"Google News RSS":"Google Новости RSS";
            else if(name.equals("bing news rss"))name=en?"Bing News RSS":"Bing Новости RSS";
            if(detail.length()>0)detail.append("; ");detail.append(name).append(": ").append(label);
        }
        return (en?"Search returned no usable sources":"Интернет-поиск не получил результаты")+(detail.length()==0?(en?". Check your connection or try a different query.":". Проверьте подключение или уточните запрос"):". "+detail);
    }
}
