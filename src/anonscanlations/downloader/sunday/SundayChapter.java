/*
 * Coded by /a/non, for /a/non
 */

package anonscanlations.downloader.sunday;

import java.io.*;
import java.net.*;
import java.util.*;

import java.math.*;
import java.security.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.*;

import anonscanlations.downloader.*;
import anonscanlations.downloader.pcviewer.*;

/**
 *
 * @author /a/non
 */
public class SundayChapter extends Chapter
{
    public static final String ERR_OTK = "Couldn't get correct OTK key";

    private String key1, key2, key3, key4, shd;

    private transient String cookies, otk;

    private String keyURL;

    public SundayChapter(){}
    
    public SundayChapter(String url, String myKeyURL)
    {
        keyURL = myKeyURL;

        key1 = getParam(url, "key1");
        key2 = getParam(url, "key2");
        key3 = getParam(url, "key3");
        key4 = getParam(url, "key4");
        shd = getParam(url, "shd");
    }

    private String getParam(String url, String param)
    {
        int index = url.indexOf(param + "="), endIndex = url.indexOf('&', index);
        if(endIndex == -1)
            endIndex = url.length();
        return(url.substring(index + param.length() + 1, endIndex));
    }
    
    public String getTitle()
    {
        return(key4.substring(0, key4.indexOf('-')));
    }

    // Dunno right now
    public int getTotal()
    {
        return(-1);
    }

    // TODO: implement this
    public boolean init() { return(true); }

    public boolean download(DownloadListener dl) throws Exception
    {
        getNewSession(keyURL);
        if(dl.isDownloadAborted())
            return(true);

        getMainFile();
        if(dl.isDownloadAborted())
            return(true);

        String xmlString = getXMLFile();
        if(dl.isDownloadAborted())
            return(true);

        if(xmlString == null || xmlString.contains("pagecount=\"0\""))
        {
            if((xmlString = backupOTKGet(dl)) == null)
            {
                throw new Exception(ERR_OTK);
            }
        }

        getSWF();
        if(dl.isDownloadAborted())
            return(true);

        // 5) parse XML

        DownloaderUtils.debug("===PART 5===");

        DownloaderUtils.debug("xmlString: " + xmlString);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        InputSource is = new InputSource(new StringReader(xmlString));
        Document d = builder.parse(is);
        Element root = d.getDocumentElement();

        String serverTimestamp = ((Element)root.getElementsByTagName("ServerTimestamp").item(0))
                                    .getAttribute("val"),
                availTimestamp = ((Element)root.getElementsByTagName("AvailableTime").item(0))
                                    .getAttribute("val"),
                server = ((Element)root.getElementsByTagName("ContentsServerPath").item(0))
                                    .getAttribute("val");
        String list[] = ((Element)root.getElementsByTagName("SamplePageList").item(0))
                                    .getAttribute("list").split("-");

        long timestamp = Long.parseLong(serverTimestamp) + Long.parseLong(availTimestamp);
        int min = Integer.parseInt(list[0]),
            max = Integer.parseInt(list[1]);

        dl.setTotal(max - min + 1);

        DownloaderUtils.debug("serverTimestamp: " + serverTimestamp);
        DownloaderUtils.debug("availTimestamp: " + availTimestamp);
        DownloaderUtils.debug("timestamp: " + timestamp);
        DownloaderUtils.debug("server: " + server);
        DownloaderUtils.debug("range: " + min + " to " + max);

        // 6) Get the pages

        DownloaderUtils.debug("===PART 6===");

        final MessageDigest md5 = MessageDigest.getInstance("MD5");

        String origURL = server + "data/" + key1 + "/" + key2 + "/" + key3 + "/" + key4 + "/bin/";

        File temp = File.createTempFile("pcviewer_temp", ".bin");

        for(int i = min; i <= max; i++)
        {
            if(dl.isDownloadAborted())
                return(true);

            String url = origURL + key3 + "_" + key4 + "_" + String.format("%04d", i) + ".bin?e=" + timestamp,
                    toHash = "e47ec34c9cf59bca8d8eb865896b74ff88f953d3" + url,
                    hashedURL = "";
            md5.reset();
            md5.update(toHash.getBytes());
            byte[] bytes = md5.digest();
            String tmp = "";
            for(int j = 0; j < bytes.length; j++)
            {
                tmp = (Integer.toHexString(0xFF & bytes[j]));
                if (tmp.length() == 1)
                    hashedURL += "0" + tmp;
                else
                    hashedURL += tmp;
            }

            url += "&h=" + hashedURL;

            DownloaderUtils.downloadFile(new URL(url), temp.getAbsolutePath());
            PCViewerDecrypt.decryptFile(temp.getAbsolutePath(),
                dl.downloadPath(this, i));

            dl.downloadIncrement(this);
        }
        temp.delete();
        
        dl.downloadFinished(this);

        return(true);
    }

    /**
     * 
     * @param url
     * @return
     * @throws IOException
     */
    private boolean getNewSession(String url) throws IOException
    {
        // 1) get otk and PHPSESSID

        DownloaderUtils.debug("===PART 1===");

        URL homePage = new URL(url);
        DownloaderUtils.debug("homePage: " + homePage);

        HttpURLConnection urlConn = (HttpURLConnection)homePage.openConnection();
        urlConn.connect();

        String headerName = null;
        for(int i = 1; (headerName = urlConn.getHeaderFieldKey(i)) != null; i++)
        {
            if(headerName.equals("Set-Cookie"))
            {
                cookies = urlConn.getHeaderField(i);
                int index = cookies.indexOf("PHPSESSID=");
                if(index != -1)
                {
                    break;
                }
            }
        }

        BufferedReader stream = new BufferedReader(
                                    new InputStreamReader(
                                        urlConn.getInputStream(), "UTF-8"));

        String line;
        boolean lastPage = true;
        int index;
        while((line = stream.readLine()) != null)
        {
            index = line.indexOf("進む &gt;</a>");
            if(index != -1)
                lastPage = false;
            
            index = line.indexOf("&otk=");
            if(index != -1)
            {
                otk = line.substring(index + 5, line.indexOf('\'', index));
                break;
            }
        }

        stream.close();
        urlConn.disconnect();
        
        DownloaderUtils.debug("otk: " + otk);

        return(lastPage);
    }

    private void getMainFile() throws IOException
    {
        // 2) get main file

        DownloaderUtils.debug("===PART 2===");

        URL mainFile = new URL("http://club.shogakukan.co.jp/dor/pcviewer_main.php?key1=" + key1 +
                            "&key2=" + key2 + "&key3=" + key3 +
                            "&key4=" + key4 + "&sp=-1&re=0&shd=" + shd +
                            "&otk=" + otk + "&vo=1");

        HttpURLConnection urlConn = (HttpURLConnection)mainFile.openConnection();
        urlConn.setRequestProperty("Cookie", cookies);
        urlConn.connect();

        // fake a read
        InputStream input = urlConn.getInputStream();
        byte buf[] = new byte[1024];
        while(input.read(buf) == 1024);

        urlConn.disconnect();
    }

    private String getXMLFile() throws IOException
    {
        // 3) get XML

        DownloaderUtils.debug("===PART 3===");

        URL xmlFile = new URL("http://club.shogakukan.co.jp/dor/pcviewer_otk.php?key1=" + key1 +
                            "&key2=" + key2 + "&key3=" + key3 +
                            "&key4=" + key4 + "&sp=-1&re=0&otk=" + otk);

        HttpURLConnection urlConn = (HttpURLConnection)xmlFile.openConnection();
        urlConn.setRequestProperty("Cookie", cookies);
        urlConn.connect();

        BufferedReader stream = new BufferedReader(new InputStreamReader(urlConn.getInputStream()));

        String line, xmlString = "";
        while((line = stream.readLine()) != null)
        {
            xmlString += line;// + '\n';
        }

        stream.close();
        urlConn.disconnect();

        if(xmlString.equals("NG"))
            xmlString = null;
        
        return(xmlString);
    }

    private void getSWF() throws IOException
    {
        // 4) by the way, read the swf

        DownloaderUtils.debug("===PART 4===");

        URL tnSwf = new URL("http://club.shogakukan.co.jp/dor/tn.swf");

        HttpURLConnection urlConn = (HttpURLConnection)tnSwf.openConnection();
        urlConn.setRequestProperty("Cookie", cookies);
        urlConn.connect();
        urlConn.disconnect();
    }

    private String backupOTKGet(DownloadListener dl) throws IOException
    {
        String xmlString = null;
        boolean lastPage = false;
        for(int i = 1; !lastPage; i++)
        {
            lastPage = getNewSession("http://club.shogakukan.co.jp/magazine/SH_CSNDY/"
                                    + key3 + "/all/" + i);
            if(dl.isDownloadAborted())
                return(null);

            getMainFile();
            if(dl.isDownloadAborted())
                return(null);

            xmlString = getXMLFile();
            if(dl.isDownloadAborted())
                return(null);
            if(xmlString != null && !xmlString.contains("pagecount=\"0\""))
                return(xmlString);
        }
        return(xmlString);
    }
}
