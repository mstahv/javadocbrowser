package org.vaadin;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.apache.tika.Tika;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

public class JavadocBrowser extends HttpServlet {

    /**
     * Maven2 repositories from which javadocs are searched
     */
    private static final String[] repos = {
            "https://repo1.maven.org/maven2" };

    public static final File DOC_CACHE = new File(
            System.getProperty("user.home") + "/jdoccache");
    public static final Tika TIKA = new Tika();

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
            if (groupId.isEmpty()) {
                listGroups(response);
                return;
            }
            String artifactId = parts[2];
            if (artifactId.isEmpty()) {
                listArtifacts(response, groupId);
                return;
            }
            String version = parts[3];
            if (version.isEmpty()) {
                listVersions(response, groupId, artifactId);
                return;
            }
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

            URL url = new URL("jar:file:" + file.getAbsolutePath() + "!/"
                    + path);
            URLConnection openConnection = url.openConnection();
            response.setHeader("Cache-Control", "max-age=" + 7 * 24 * 60 * 60);
            setContentTypeHeader(response, path);
            response.setContentLength(openConnection.getContentLength());
            InputStream inputStream = openConnection.getInputStream();
            IOUtils.copy(inputStream, response.getOutputStream());
            inputStream.close();
        } catch (Exception e) {
            printErrorPage(response, e);
        }

    }

    private void listGroups(HttpServletResponse response) throws IOException {
        listFolders(response, DOC_CACHE, "Groups");
    }

    private void listVersions(HttpServletResponse response, String groupId,
            String artifactId) throws IOException {
        File file = new File(DOC_CACHE, groupId + "/" + artifactId);
        listFolders(response, file, "Versions for " + artifactId);
    }

    private void listArtifacts(HttpServletResponse response, String groupId)
            throws IOException {
        File file = new File(DOC_CACHE, groupId);
        listFolders(response, file, "Artifacts for " + groupId);
    }

    private void listFolders(HttpServletResponse response, File file,
            String caption) throws IOException {
        if (!file.exists()) {
            printErrorPage(response, new Exception("Not found"));
        } else {
            response.setContentType("text/html");
            ServletOutputStream outputStream = response.getOutputStream();
            header(outputStream, caption);
            File[] listFiles = file.listFiles();
            for (File file2 : listFiles) {
                if (file2.isDirectory() && !file2.isHidden()) {
                    outputStream.println("<a href='" + file2.getName() + "/'>"
                            + file2.getName() + "</a><br>");
                }
            }
            footer(outputStream);
        }
    }

    private void footer(ServletOutputStream outputStream) throws IOException {
        outputStream.println("</html></body>");
    }

    private void header(ServletOutputStream outputStream, String caption)
            throws IOException {
        outputStream.println(String.format(
                "<html><head><title>%s</title></head><body><h1>%1$s</h1>",
                caption));
    }

    private void printErrorPage(HttpServletResponse response, Exception e)
            throws IOException {
        e.printStackTrace();
        response.setStatus(404);
        response.getOutputStream().println("Ooops! Javadocs not found!?");
    }

    private void cache(String artifactId, String version, String groupId,
            File cachefile) throws FileNotFoundException {
        String artifactName = artifactId + "-" + version;
        if (!cachefile.exists()) {
            for (String repo : repos) {
                String repopath = "/" + groupId.replace(".", "/") + "/"
                        + artifactId + "/" + version + "/";
                try {
                    URL url = new URL(repo + repopath + artifactName
                            + "-javadoc.jar");
                    InputStream openStream = url.openStream();
                    cachefile.getParentFile().mkdirs();
                    IOUtils.copy(openStream, new FileOutputStream(cachefile));
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

    private void setContentTypeHeader(HttpServletResponse response, String path) {
        String mimeType = null;
        int idx = path.lastIndexOf('.');
        if (idx == -1) {
            mimeType = "application/octet-stream";
        } else {
            String fileExtension = path.substring(idx).toLowerCase();
            if (fileExtension.equals(".js")) {
                mimeType = "application/javascript";
            } else if (fileExtension.equals(".xml")) {
                mimeType = "application/xml";
            } else if (fileExtension.equals(".json")) {
                mimeType = "application/json";
            } else if (fileExtension.equals(".txt")) {
                mimeType = "text/plain";
            } else if (fileExtension.equals(".html")) {
                mimeType = "text/html";
            } else if (fileExtension.equals(".css")) {
                mimeType = "text/css";
            } else {
                mimeType = TIKA.detect(path);
            }
        }
        response.setContentType(mimeType);
    }

}
