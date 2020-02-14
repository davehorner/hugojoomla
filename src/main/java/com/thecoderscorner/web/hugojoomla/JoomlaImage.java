package com.thecoderscorner.web.hugojoomla;

import com.google.common.base.Strings;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class JoomlaImage {
    public static final JoomlaImage EMPTY = new JoomlaImage();
    private final String alt;
    private final String url;
    private final String htmlClass;

    private JoomlaImage() {
        alt="";
        url="";
        htmlClass="";
    }

    public JoomlaImage(String imgType, String json) throws ParseException {
	if(false) {
        JSONObject imageBlock;
        JSONParser parser = new JSONParser();
        imageBlock = (JSONObject) parser.parse(json);
        this.url = imageBlock.getOrDefault("image_" + imgType, "").toString();
        htmlClass = imageBlock.getOrDefault("float_" + imgType, "").toString();
        alt = imageBlock.getOrDefault("image_" + imgType + "_alt", "").toString();
        if(!Strings.isNullOrEmpty(url) && !url.startsWith("http")) {
            url = "/" + url;
        }
	} else {
        this.url = json;
        htmlClass = "";
        alt = json;
	}
    }

    public boolean isImagePresent() {
        return !Strings.isNullOrEmpty(url);
    }

    public String getAlt() {
        return alt;
    }

    public String getUrl() {
        return url;
    }

    public String getHtmlClass() {
        return htmlClass;
    }
}
