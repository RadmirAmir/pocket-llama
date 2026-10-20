package dev.pocketllama.search;

import org.json.*;

/** Only fixed diagnostic codes are displayed; server tracebacks/URLs never enter errors. */
public final class SearchErrors {
    private SearchErrors(){}
    public static String describe(JSONObject failure){
        if("search_internal_error".equals(failure.optString("error")))return "Внутренняя ошибка SearXNG. Поисковый запрос не выполнен";
        StringBuilder detail=new StringBuilder();JSONArray diagnostics=failure.optJSONArray("diagnostics");
        if(diagnostics!=null)for(int i=0;i<Math.min(4,diagnostics.length());i++){
            JSONObject item=diagnostics.optJSONObject(i);if(item==null)continue;String name=item.optString("engine"),reason=item.optString("reason");
            if(!java.util.Arrays.asList("google","brave","duckduckgo","bing").contains(name))continue;
            String label;
            switch(reason){
                case "dns":label="не удалось определить адрес сервера (DNS)";break;
                case "certificate":label="ошибка сертификата HTTPS; проверьте дату телефона и настройки сети";break;
                case "tls":label="сбой HTTPS-соединения";break;
                case "captcha":label="поисковик требует CAPTCHA";break;
                case "rate_limited":label="поисковик ограничил частоту запросов";break;
                case "access_denied":label="поисковик отклонил запрос";break;
                case "timeout":label="истекло время ожидания";break;
                case "connection":label="не удалось подключиться";break;
                case "http_error":label="ошибка ответа поисковика";break;
                default:label="внутренняя ошибка поиска";
            }
            if(detail.length()>0)detail.append("; ");detail.append(name).append(": ").append(label);
        }
        return "Интернет-поиск не получил результаты"+(detail.length()==0?". Проверьте подключение и повторите запрос":". "+detail);
    }
}
