package com.thecoderscorner.web.hugojoomla;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class JoomlaHugoConverter {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final JdbcTemplate template;
    private final String pathToOutput;
    private boolean buildTags;
    private final NastyContentChecker nastyContentChecker;


    private final Template contentTemplate;
    private final Template categoryTemplate;
    private final Multimap<Integer, String> tagsByName = LinkedListMultimap.create(100);
    private final String dbExtension;

    public JoomlaHugoConverter(NastyContentChecker nastyContentChecker,
                               JdbcTemplate template, String pathToOutput, String dbExtension,
                               boolean buildTags) throws IOException {

        this.dbExtension = dbExtension;

        this.nastyContentChecker = nastyContentChecker;
        this.template = template;
        this.pathToOutput = pathToOutput;
        this.buildTags = buildTags;

        Configuration cfg = new Configuration(Configuration.getVersion());
        cfg.setClassLoaderForTemplateLoading(ClassLoader.getSystemClassLoader(), "/");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(true);

        categoryTemplate = cfg.getTemplate("categoryPage.toml.ftl");
        contentTemplate = cfg.getTemplate("defaultPage.toml.ftl");

        buildTags();
    }

    private void buildTags() {
        if( ! buildTags) {
            logger.warn("Tag processing turned off, not processing any tags");
            return;
        }

        String sqlTags ="select M.content_item_id as id, T.title as name\n" +
                        " from REPLSTR_tags T, REPLSTR_contentitem_tag_map M\n" +
                        " where T.id = M.tag_id\n" +
                        "   and M.type_alias = 'com_content.article'";
        sqlTags = sqlTags.replace("REPLSTR", dbExtension);

        List<TagInfo> tags = template.query(sqlTags, (resultSet, i) -> new TagInfo(
                resultSet.getString("name"),
                resultSet.getInt("id")
        ));

        tags.forEach(t-> tagsByName.put(t.getContentId(), t.getTagName()));
        logger.info("Loaded {} tags into system", tags.size());
    }


    public void performConversion() {
        try {
            logger.info("Starting conversion of Joomla database");
            String articleQuery =
                    "select C.id as id, U.username as username, C.created as created, C.introtext as intro, " +
                    "       C.`fulltext` as full, D.path as path, C.title as title, C.alias as alias,\n" +
                    "       C.images as images, C.state as state, D.alias as catAlias \n" +
                    "from REPLSTR_content C, REPLSTR_users U, REPLSTR_categories D\n" +
                    "where C.created_by = U.id\n" +
                    "  and D.id = C.catid\n" +
                    "  and D.path <> 'uncategorised'\n";
            articleQuery = articleQuery.replace("REPLSTR", dbExtension);

            List<JoomlaContent> content = template.query(articleQuery, (resultSet, i) -> new JoomlaContent(
                    resultSet.getInt("id"),
                    resultSet.getInt("state"),
                    resultSet.getString("username"),
                    resultSet.getDate("created").toLocalDate(),
                    resultSet.getString("intro"),
                    resultSet.getString("full"),
                    resultSet.getString("path"),
                    resultSet.getString("title"),
                    resultSet.getString("alias"),
                    resultSet.getString("images"),
                    resultSet.getString("catAlias")
            ));
            logger.info("(** "+articleQuery);

            content.stream().filter(JoomlaContent::isPublished).forEach(c-> {
                nastyContentChecker.checkForNastyContent(c);
                Path path = Paths.get(pathToOutput);
                logger.info("processing {} {} {}", c.getTitle(), c.getCategory(), c.getAlias());
                Path newPath = path.resolve(c.getCategory());
                newPath.toFile().mkdirs();
                buildTomlOutput(c, newPath.resolve(c.getId() + "-" + c.getAlias() + ".md"), contentTemplate);
            });

            content.stream().filter(c-> !c.isPublished()).forEach(
                    c->logger.info("Skipping deleted content {} {}", c.getTitle(), c.getId())
            );

            performCategoryConversion();

            logger.info("Finished conversion of Joomla database");
        }
        catch(Exception e) {
            logger.error("Did not complete conversion", e);
        }
    }

    private void performCategoryConversion() {
        String sqlCat =
                "SELECT C.id AS id, C.alias AS alias, C.description AS description, C.path AS path,\n" +
                "       C.created_time AS created_time, C.published AS published, C.description description, C.title AS title,\n" +
                "       P.alias AS parent\n" +
                "FROM REPLSTR_categories C, REPLSTR_categories P\n" +
                "WHERE C.parent_id = P.id\n";
        sqlCat = sqlCat.replace("REPLSTR", dbExtension);
        List<JoomlaContent> content = template.query(sqlCat, (resultSet, i) -> new JoomlaContent(
                resultSet.getInt("id"),
                resultSet.getInt("published"),
                "system",
                resultSet.getDate("created_time").toLocalDate(),
                resultSet.getString("title"),
                resultSet.getString("description"),
                resultSet.getString("parent"),
                resultSet.getString("title"),
                resultSet.getString("alias"),
                "{}",
                resultSet.getString("path")

        ));

        content.stream().filter(JoomlaContent::isPublished).forEach(c-> {
            nastyContentChecker.checkForNastyContent(c);
            Path path = Paths.get(pathToOutput);
            logger.info("processing category {} {} {}", c.getTitle(), c.getCategory(), c.getAlias());
            Path newPath = path.resolve(c.getParent() + ".md");
            buildTomlOutput(c, newPath, categoryTemplate);
        });

        logger.info("Category description creation complete");
    }


    public void buildTomlOutput(JoomlaContent content, Path resolve, Template template)  {

        try {
            String tagsQuoted = tagsByName.get(content.getId()).stream()
                    .map(t -> "\"" + t + "\"")
                    .collect(Collectors.joining(", "));

            Map<String, Object> root = new HashMap<>();
            root.put("joomlaData", content);
            root.put("tags", tagsQuoted);
            root.put("body", urlSorter(content.getIntro() + "\n" + content.getBody()));
            template.process(root, new BufferedWriter(new FileWriter(resolve.toFile())));
        } catch (Exception e) {
            logger.error("Failed to generate file", e);
        }
    }

    private String urlSorter(String body) {
        String sqlForArticleLink = "SELECT C.alias AS alias, D.path AS path\n" +
                "FROM REPLSTR_content C, REPLSTR_categories D\n" +
                "WHERE C.id=? AND C.catid = D.id\n";
        sqlForArticleLink = sqlForArticleLink.replace("REPLSTR", dbExtension);

        Pattern linkPattern = Pattern.compile("index.php.option=com_content.amp.view=article.amp.id=([0-9]*).amp.catid=([0-9]*).amp.Itemid=([0-9]*)");

        body = ensureAllImageUrlsAreCorrect(body);

        boolean foundSomething = true;
        while (foundSomething) {
            Matcher matcher = linkPattern.matcher(body);
            if (matcher.find()) {
                int id = Integer.parseInt(matcher.group(1));
                String url = template.queryForObject(sqlForArticleLink, new Object[]{id}, (resultSet, i) ->
                        "/" + resultSet.getString("path") + "/" + id + "-" + resultSet.getString("alias"));
                body = body.replace(matcher.group(0), url);
                logger.info("  Rewrote url {}", url);
            } else {
                foundSomething = false;
            }
        }
        return body;
    }

    private String ensureAllImageUrlsAreCorrect(String body) {
        Pattern imgPattern = Pattern.compile("\\<img.*?src=\"([^\"]*)\"");

        // TODO this is a hack, think up a better way. However, it probably does the job for most sites.
        Matcher imgMatcher = imgPattern.matcher(body);
        for(int i=0;i<100;i++) {
            if(imgMatcher.find()) {
                String imgSrc = imgMatcher.group(1);
                if(!imgSrc.startsWith("http") && !imgSrc.startsWith("/")) {
                    imgSrc = "/" + imgSrc;
                    body = body.replace("src=\"" + imgMatcher.group(1) + "\"", "src=\"" + imgSrc + "\"");
                    imgMatcher = imgPattern.matcher(body);
                }
            }
        }
        return body;
    }
}
