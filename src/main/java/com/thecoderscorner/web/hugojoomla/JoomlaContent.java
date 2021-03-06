package com.thecoderscorner.web.hugojoomla;

import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

public class JoomlaContent {
    private final static Logger LOGGER = LoggerFactory.getLogger(JoomlaContent.class);
    private final static Pattern HYPERLINK_PATTERN = Pattern.compile("\\<a[^\\>]*\\>([^\\<]*)\\<\\/a\\>");
    private final static Pattern CODE_BLOCK_PATTERN  = Pattern.compile("(\\<pre\\>[^\\<]*\\<code\\>)|(\\</code\\>[^\\<]*\\</pre\\>)");

    private final static DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
    private final int id;
    private final String author;
    private final LocalDate createdDate;
    private final String intro;
    private final String body;
    private final String category;
    private final String title;
    private final String alias;
    private final int status;
    private final String parent;
    private JoomlaImage introImage = JoomlaImage.EMPTY;
    private JoomlaImage bodyImage = JoomlaImage.EMPTY;

    public JoomlaContent(int id, int status, String author, LocalDate createdDate, String intro,
                         String body, String category, String title, String alias,
                         String images, String parent) {
        this.id = id;
        this.status = status;
        this.author = author;
        this.createdDate = createdDate;
        this.intro = body;
        this.body = intro;
        this.category = category;
        this.title = title;
        this.alias = alias;
        this.parent = parent;
        try {
	    if(!images.isEmpty()) {
            introImage = new JoomlaImage("intro", images);
            bodyImage = new JoomlaImage("fulltext", images);
	    }
        } catch (ParseException e) {
            LOGGER.warn("Images for " + id +" not processed ("+images,e);
        }
    }

    public String getAlias() {
        return alias;
    }

    public int getId() {
        return id;
    }

    public String getAuthor() {
        return author;
    }

    public String getCreatedDateAsText() {
        return formatter.format(createdDate);
    }

    public String getIntro() {
        return intro;
    }

    public String getIntroAsSingleLine() {
        return escapeIt(intro);
    }

    public String escapeIt(String s) {
        s = s.replaceAll("[\\n\\r]*", "");
        s = s.replace("\"", "\\\"");
        return escapeAllCode(removeAllHyperlinks(s.replace("\'", "\\\"")));
    }

    public String escapeAllCode(String s) {
        return CODE_BLOCK_PATTERN.matcher(s).replaceAll("\n```\n");
    }

    public String removeAllHyperlinks(String s) {
        return HYPERLINK_PATTERN.matcher(s).replaceAll("\\1");
    }

    public String getBody() {
        return escapeAllCode(body);
    }

    public String getCategory() {
        return category;
    }

    public String getTitle() {
        return escapeIt(title);
    }

    public JoomlaImage getIntroImage() {
        return introImage;
    }

    public JoomlaImage getBodyImage() {
        return bodyImage;
    }

    public boolean isPublished() {
        return status == 0 || status == 1;
    }

    public String getParent() {
        return parent;
    }
}
