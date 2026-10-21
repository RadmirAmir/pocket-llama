package dev.pocketllama.agent;

import java.util.Locale;
import java.util.regex.Pattern;
import org.json.*;

/** Request-local routing constraints. The model still writes every external query. */
public final class SearchIntent {
    private static final Pattern NEWS=Pattern.compile("новост|\\bnews\\b|что (?:происходит|произошло)",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private static final Pattern SEARCH=Pattern.compile("найди|найти|поищи|погугли|проверь.{0,30}(?:интернет|сети|сайт)|через поиск|\\b(?:search|browse|look up|google it)\\b",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private static final Pattern CURRENT=Pattern.compile("сегодня|сейчас|последни[йехм]|актуальн|свежи[йехм]|текущ|\\b(?:latest|current|today|recent|now)\\b",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private static final Pattern OFFLINE=Pattern.compile("без (?:интернета|поиска)|не (?:ищи|гугли|пользуйся (?:интернетом|поиском)|используй (?:интернет|поиск))|\\b(?:do not|don't) (?:search|browse)|\\bwithout (?:search|internet)\\b",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    public final boolean required,news,forbidden,greeting;
    private SearchIntent(boolean required,boolean news,boolean forbidden,boolean greeting){this.required=required;this.news=news;this.forbidden=forbidden;this.greeting=greeting;}
    public static SearchIntent from(JSONObject body)throws JSONException {
        JSONArray messages=body.getJSONArray("messages");String latest="",previous="";
        for(int i=messages.length()-1;i>=0;i--)if("user".equals(messages.getJSONObject(i).optString("role"))){if(latest.isEmpty())latest=text(messages.getJSONObject(i));else{previous=text(messages.getJSONObject(i));break;}}
        // Follow-up "use search" refers to the previous task, not the assistant's
        // often incorrect refusal. A new, standalone task does not inherit it.
        boolean search=SEARCH.matcher(latest).find();String task=latest;
        if(search&&latest.length()<100&&!NEWS.matcher(latest).find()&&!CURRENT.matcher(latest).find())task+="\n"+previous;
        boolean forbidden=OFFLINE.matcher(latest).find();
        boolean definition=latest.toLowerCase(Locale.ROOT).matches("(?s)^(?:что такое|как работает|объясни (?:что|как)|what is|how does).*?");
        boolean creative=latest.toLowerCase(Locale.ROOT).matches("(?s)^(?:переведи|перефразируй|сочини|напиши (?:стих|рассказ|письмо)|translate|rewrite|write (?:a )?(?:poem|story|email)).*");
        definition|=creative;
        boolean news=NEWS.matcher(task).find()&&!definition;
        boolean greeting=latest.trim().toLowerCase(Locale.ROOT).matches("(?:привет|здравствуй(?:те)?|спасибо|hello|hi|thanks|thank you)[!.,\\s]*");
        return new SearchIntent(!forbidden&&(search||news||CURRENT.matcher(task).find()&&!definition),news,forbidden,greeting);
    }
    public static String text(JSONObject message){
        Object content=message.opt("content");if(content instanceof String)return (String)content;
        StringBuilder out=new StringBuilder();if(content instanceof JSONArray){JSONArray parts=(JSONArray)content;for(int i=0;i<parts.length();i++){JSONObject part=parts.optJSONObject(i);if(part!=null&&"text".equals(part.optString("type")))out.append(part.optString("text")).append('\n');}}
        return out.toString();
    }
}
