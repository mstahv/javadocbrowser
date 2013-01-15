package org.vaadin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;

@WebServlet("/*")
public class JavadocBrowser extends HttpServlet {

    /**
     * Maven2 repositories from which javadocs are searched
     */
    private static final String[] repos = { "http://maven.vaadin.com/vaadin-addons" ,"http://repo1.maven.org/maven2" };

    public static final File DOC_CACHE = new File(
            System.getProperty("user.home") + "/jdoccache");

    @Override
    public void init() throws ServletException {
        super.init();
        DOC_CACHE.mkdirs();
    }

    @Override
    protected void service(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        try {
            String[] parts = request.getPathInfo().split("\\/", 5);
            String groupId = parts[1];
            String artifactId = parts[2];
            String version = parts[3];
            String path = parts[4];

            File file = new File(DOC_CACHE, groupId + "/" + artifactId + "/"
                    + version + "/" + artifactId + "-" + version
                    + "-javadoc.jar");
            if (!file.exists()) {
                cache(artifactId, version, groupId, file);
            }

            if (path.isEmpty()) {
                path = "index.html";
            }

            URL url = new URL("jar:file:" + file.getAbsolutePath() + "!/" + path);
            URLConnection openConnection = url.openConnection();
            response.setHeader("Cache-Control", "max-age=" + 7 * 24 * 60 * 60);
            response.setContentType(openConnection.getContentType());
            response.setContentLength(openConnection.getContentLength());
            InputStream inputStream = openConnection.getInputStream();
            IOUtils.copy(inputStream,
                    response.getOutputStream());
            inputStream.close();
        } catch (Exception e) {
            printErrorPage(response, e);
        }

    }

    private void printErrorPage(HttpServletResponse response, Exception e)
            throws IOException {
        e.printStackTrace();
        response.setStatus(404);
        response.getOutputStream().println("Ooops! Javadocs not found!?");
    }

    private void cache(String artifactId, String version, String groupId, File cachefile) throws FileNotFoundException {
        String artifactName = artifactId + "-" + version;
        if (!cachefile.exists()) {
            cachefile.getParentFile().mkdirs();

            for (String repo : repos) {
                String repopath = "/" + groupId.replace(".", "/") + "/"
                        + artifactId + "/" + version + "/";
                try {
                    URL url = new URL(repo + repopath + artifactName
                            + "-javadoc.jar");
                    InputStream openStream = url.openStream();
                    IOUtils.copy(openStream, new FileOutputStream(
                            cachefile));
                    openStream.close();
                    break;
                } catch (Exception e) {
                }
            }
        }

        if (!cachefile.exists()) {
            throw new FileNotFoundException();
        }

    }

}
