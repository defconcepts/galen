/*******************************************************************************
* Copyright 2015 Ivan Shubin http://galenframework.com
* 
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* 
*   http://www.apache.org/licenses/LICENSE-2.0
* 
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
******************************************************************************/
package com.galenframework.utils;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import com.galenframework.browser.SeleniumGridBrowserFactory;
import com.galenframework.tests.GalenProperties;
import com.galenframework.tests.TestSession;
import com.galenframework.browser.SeleniumBrowser;
import com.galenframework.browser.SeleniumBrowserFactory;
import com.galenframework.config.GalenConfig;
import com.galenframework.config.GalenProperty;
import com.galenframework.rainbow4j.Rainbow4J;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.Platform;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GalenUtils {

    private final static Logger LOG = LoggerFactory.getLogger(GalenUtils.class);
    
    private static final String URL_REGEX = "[a-zA-Z0-9]+://.*";
    public static final String JS_RETRIEVE_DEVICE_PIXEL_RATIO =
            "window.devicePixelRatio = window.devicePixelRatio || " +
            "window.screen.deviceXDPI / window.screen.logicalXDPI; " +
            "var pr = window.devicePixelRatio; if (pr != undefined && pr != null) return pr; else return 1.0;";

    public static final int ZERO_WIDTH_SPACE_CHAR = 65279;

    public static boolean isUrl(String url) {
        if (url == null) {
            return false;
        }
        return url.matches(URL_REGEX) || url.equals("-");
    }
    
    public static String formatScreenSize(Dimension screenSize) {
        if (screenSize != null) {
            return String.format("%dx%d", screenSize.width, screenSize.height);
        }
        else return "0x0";
    }

    public static Dimension readSize(String sizeText) {
        if (sizeText == null) {
            return null;
        }
        if (!sizeText.matches("[0-9]+x[0-9]+")) {
            throw new RuntimeException("Incorrect screen size: " + sizeText);
        }
        else {
            String[] arr = sizeText.split("x");
            return new Dimension(Integer.parseInt(arr[0]), Integer.parseInt(arr[1]));
        }
    }

    public static File findFile(String specFile) {
        URL resource = GalenUtils.class.getResource(specFile);
        if (resource != null) {
            return new File(resource.getFile());
        }
        else return new File(specFile);
    }
    
    
    public static File makeFullScreenshot(WebDriver driver) throws IOException, InterruptedException {
        // scroll up first
        scrollVerticallyTo(driver, 0);
        byte[] bytes = ((TakesScreenshot)driver).getScreenshotAs(OutputType.BYTES);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        int capturedWidth = image.getWidth();
        int capturedHeight = image.getHeight();

        long longScrollHeight = (Long)((JavascriptExecutor)driver).executeScript("return Math.max(" +
                "document.body.scrollHeight, document.documentElement.scrollHeight," +
                "document.body.offsetHeight, document.documentElement.offsetHeight," +
                "document.body.clientHeight, document.documentElement.clientHeight);"
            );

        Double devicePixelRatio = ((Number)((JavascriptExecutor)driver).executeScript(JS_RETRIEVE_DEVICE_PIXEL_RATIO)).doubleValue();

        int scrollHeight = (int)longScrollHeight;

        File file = File.createTempFile("screenshot", ".png");

        int adaptedCapturedHeight = (int)(((double)capturedHeight) / devicePixelRatio);

        BufferedImage resultingImage;

        if (Math.abs(adaptedCapturedHeight - scrollHeight) > 40) {
            int scrollOffset = adaptedCapturedHeight;
            
            int times = scrollHeight / adaptedCapturedHeight;
            int leftover = scrollHeight % adaptedCapturedHeight;

            final BufferedImage tiledImage = new BufferedImage(capturedWidth, (int)(((double)scrollHeight) * devicePixelRatio), BufferedImage.TYPE_INT_RGB);
            Graphics2D g2dTile = tiledImage.createGraphics();
            g2dTile.drawImage(image, 0,0, null);

            
            int scroll = 0;
            for (int i = 0; i < times - 1; i++) {
                scroll += scrollOffset;
                scrollVerticallyTo(driver, scroll);
                BufferedImage nextImage = ImageIO.read(new ByteArrayInputStream(((TakesScreenshot)driver).getScreenshotAs(OutputType.BYTES)));
                g2dTile.drawImage(nextImage, 0, (i+1) * capturedHeight, null);
            }
            if (leftover > 0) {
                scroll += scrollOffset;
                scrollVerticallyTo(driver, scroll);
                BufferedImage nextImage = ImageIO.read(new ByteArrayInputStream(((TakesScreenshot)driver).getScreenshotAs(OutputType.BYTES)));
                BufferedImage lastPart = nextImage.getSubimage(0, nextImage.getHeight() - (int)(((double)leftover) * devicePixelRatio), nextImage.getWidth(), leftover);
                g2dTile.drawImage(lastPart, 0, times * capturedHeight, null);
            }
            
            scrollVerticallyTo(driver, 0);

            resultingImage = tiledImage;
        }
        else {
            resultingImage = image;
        }

        if (GalenConfig.getConfig().shouldAutoresizeScreenshots()) {
            try {
                resultingImage = GalenUtils.resizeScreenshotIfNeeded(driver, resultingImage);
            } catch (Exception ex) {
                LOG.trace("Couldn't resize screenshot", ex);
            }
        }

        ImageIO.write(resultingImage, "png", file);
        return file;
    }


    /**
     * Check the devicePixelRatio and adapts the size of the screenshot as if the ratio was 1.0
     * @param driver
     * @param screenshotImage
     * @return
     */
    public static BufferedImage resizeScreenshotIfNeeded(WebDriver driver, BufferedImage screenshotImage) {
        Double devicePixelRatio = 1.0;

        try {
            devicePixelRatio = ((Number) ((JavascriptExecutor) driver).executeScript(JS_RETRIEVE_DEVICE_PIXEL_RATIO)).doubleValue();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        if (devicePixelRatio > 1.0 && screenshotImage.getWidth() > 0) {
            Long screenSize = (Long) ((JavascriptExecutor) driver).executeScript("return Math.max(" +
                            "document.body.scrollWidth, document.documentElement.scrollWidth," +
                            "document.body.offsetWidth, document.documentElement.offsetWidth," +
                            "document.body.clientWidth, document.documentElement.clientWidth);"
            );

            Double estimatedPixelRatio = ((double)screenshotImage.getWidth()) / ((double)screenSize);

            if (estimatedPixelRatio > 1.0) {

                int newWidth = (int) (screenshotImage.getWidth() / estimatedPixelRatio);
                int newHeight = (int) (screenshotImage.getHeight() / estimatedPixelRatio);

                Image tmp = screenshotImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
                BufferedImage scaledImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);

                Graphics2D g2d = scaledImage.createGraphics();
                g2d.drawImage(tmp, 0, 0, null);
                g2d.dispose();

                return scaledImage;
            }
            else return screenshotImage;
        }
        else return screenshotImage;
    }

    public static void scrollVerticallyTo(WebDriver driver, int scroll) {
        ((JavascriptExecutor)driver).executeScript("window.scrollTo(0, " + scroll + ");");
        try {
            waitUntilItIsScrolledToPosition(driver, scroll);
        } catch (InterruptedException e) {
            LOG.trace("Interrupt error during scrolling occurred.", e);
        }
    }

    private static void waitUntilItIsScrolledToPosition(WebDriver driver, int scrollPosition) throws InterruptedException {
        int hardTime = GalenConfig.getConfig().getIntProperty(GalenProperty.SCREENSHOT_FULLPAGE_SCROLLWAIT);
        if (hardTime > 0) {
            Thread.sleep(hardTime);
        }
        int time = GalenConfig.getConfig().getIntProperty(GalenProperty.SCREENSHOT_FULLPAGE_SCROLLTIMEOUT);
        boolean isScrolledToPosition = false;
        while(time >= 0 && !isScrolledToPosition) {
            Thread.sleep(50);
            time -= 50;
            isScrolledToPosition = Math.abs(obtainVerticalScrollPosition(driver) - scrollPosition) < 3;
        }
    }

    private static int obtainVerticalScrollPosition(WebDriver driver) {
        Long scrollLong = (Long) ((JavascriptExecutor)driver).executeScript("return (window.pageYOffset !== undefined) ? window.pageYOffset : (document.documentElement || document.body.parentNode || document.body).scrollTop;");
        return scrollLong.intValue();
    }

    public static String convertToFileName(String name) {
        return name.toLowerCase().replaceAll("[^\\dA-Za-z\\.\\-]", " ").replaceAll("\\s+", "-");
    }

    
    /**
     * Needed for Javascript based tests
     * @param browserType
     * @return
     */
    public static WebDriver createDriver(String browserType, String url, String size) {
        if (browserType == null) { 
            browserType = GalenConfig.getConfig().getDefaultBrowser();
        }
        
        SeleniumBrowser browser = (SeleniumBrowser) new SeleniumBrowserFactory(browserType).openBrowser();
        
        if (url != null && !url.trim().isEmpty()) {
            browser.load(url);    
        }
        
        if (size != null && !size.trim().isEmpty()) {
            browser.changeWindowSize(GalenUtils.readSize(size));
        }
        
        return browser.getDriver();
    }
    
    public static WebDriver createGridDriver(String gridUrl, String browserName, String browserVersion, String platform, Map<String, String> desiredCapabilities, String size) {
        SeleniumGridBrowserFactory factory = new SeleniumGridBrowserFactory(gridUrl);
        factory.setBrowser(browserName);
        factory.setBrowserVersion(browserVersion);
        
        if (platform != null) {
            factory.setPlatform(Platform.valueOf(platform));
        }
        
        if (desiredCapabilities != null) {
            factory.setDesiredCapabilites(desiredCapabilities);
        }
        
        WebDriver driver = ((SeleniumBrowser)factory.openBrowser()).getDriver();
        
        GalenUtils.resizeDriver(driver, size);
        return driver;
    }
    
    public static void resizeDriver(WebDriver driver, String sizeText) {
        if (sizeText != null && !sizeText.trim().isEmpty()) {
            Dimension size = GalenUtils.readSize(sizeText);
            resizeDriver(driver, size.width, size.height);
        }
    }

    public static void resizeDriver(WebDriver driver, int width, int height) {
        if (GalenConfig.getConfig().getBooleanProperty(GalenProperty.GALEN_BROWSER_VIEWPORT_ADJUSTSIZE)) {
            GalenUtils.autoAdjustBrowserWindowSizeToFitViewport(driver, width, height);
        } else {
            driver.manage().window().setSize(new org.openqa.selenium.Dimension(width, height));
        }
    }

    public static File takeScreenshot(WebDriver driver) throws IOException {
        File file = ((TakesScreenshot)driver).getScreenshotAs(OutputType.FILE);


        if (GalenConfig.getConfig().shouldAutoresizeScreenshots()) {
            BufferedImage image = Rainbow4J.loadImage(file.getAbsolutePath());
            File newFile = File.createTempFile("screenshot", ".png");
            image = GalenUtils.resizeScreenshotIfNeeded(driver, image);

            Rainbow4J.saveImage(image, newFile);
            return newFile;
        }
        else return file;
    }
    
    public static Properties loadProperties(String fileName) throws IOException {
        
        GalenProperties properties = null;
        if (TestSession.current() != null) {
            properties = TestSession.current().getProperties();
        }
        else properties = new GalenProperties();
        
        properties.load(new File(fileName));
        return properties.getProperties();
    }
    
    public static void cookie(WebDriver driver, String cookie) {
        String script = "document.cookie=\"" + StringEscapeUtils.escapeJava(cookie) + "\";";
        injectJavascript(driver, script);
    }
    
    public static Object injectJavascript(WebDriver driver, String script) {
        return ((JavascriptExecutor)driver).executeScript(script);
    }
    
    public static String readFile(String fileName) throws IOException {
        return FileUtils.readFileToString(new File(fileName));
    }
    
    public static Object[] listToArray(List<?> list) {
        if (list == null) {
            return new Object[]{};
        }
        Object[] arr = new Object[list.size()];
        return list.toArray(arr);
    }

    public static String getParentForFile(String filePath) {
        if (filePath != null) {
            return new File(filePath).getParent();
        }
        else return null;
    }

    public static InputStream findFileOrResourceAsStream(String filePath) throws FileNotFoundException {
        File file = new File(filePath);

        if (file.exists()) {
            return new FileInputStream(file);
        }
        else {
            if (!filePath.startsWith("/")) {
                filePath = "/" + filePath;
            }
            InputStream stream = GalenUtils.class.getResourceAsStream(filePath);
            if (stream != null) {
                return stream;
            }
            else {
                String windowsFilePath = filePath.replace("\\", "/");
                return GalenUtils.class.getResourceAsStream(windowsFilePath);
            }
        }

    }

    public static String calculateFileId(String fullPath) {
        try {
            String fileName = new File(fullPath).getName();
            MessageDigest md = MessageDigest.getInstance("MD5");
            InputStream is = GalenUtils.findFileOrResourceAsStream(fullPath);
            new DigestInputStream(is, md);
            byte [] hashBytes = md.digest();
            return fileName + convertHashBytesToString(hashBytes);

        } catch (Exception ex) {
            throw new RuntimeException("Could not calculate file id", ex);
        }

    }

    private static String convertHashBytesToString(byte[] hashBytes) {
        StringBuilder builder = new StringBuilder();
        for (byte b : hashBytes) {
            builder.append(Integer.toHexString(0xFF & b));
        }
        return builder.toString();
    }


    public static Pattern convertObjectNameRegex(String regex) {
        String jRegex = regex.replace("#", "[0-9]+").replace("*", ".*");
        return Pattern.compile(jRegex);
    }

    public static boolean isObjectExpression(String singleExpression) {
        for (int i = 0; i < singleExpression.length(); i++) {
            char symbol = singleExpression.charAt(i);
            if (symbol == '*' || symbol == '#') {
                return true;
            }
        }
        return false;
    }


    public static String toCommaSeparated(List<String> list) {
        if (list != null) {
            StringBuffer buff = new StringBuffer();
            boolean comma = false;
            for (String item : list) {
                if (comma) {
                    buff.append(',');
                }
                comma = true;
                buff.append(item);
            }
            return buff.toString();
        }
        return "";
    }

    public static String removeNonPrintableControlSymbols(String line) {
        StringBuilder builder = new StringBuilder();

        char ch;
        for (int i = 0; i < line.length(); i++) {
            ch = line.charAt(i);

            if (ch >= 32 && ch < ZERO_WIDTH_SPACE_CHAR || ch == 9) {
                builder.append(ch);
            }

        }
        return builder.toString();
    }

    public static Dimension getViewportArea(WebDriver driver) {
        List<Long> size = (List<Long>)((JavascriptExecutor)driver).executeScript("return [document.documentElement.clientWidth" +
                        "|| document.body.clientWidth" +
                        "|| window.innerWidth," +
                        "document.documentElement.clientHeight" +
                        "|| document.body.clientHeight" +
                        "|| window.innerHeight];"
        );
        return new Dimension(size.get(0).intValue(), size.get(1).intValue());
    }

    public static void autoAdjustBrowserWindowSizeToFitViewport(WebDriver driver, int width, int height) {
        driver.manage().window().setSize(new org.openqa.selenium.Dimension(width, height));
        Dimension viewport = getViewportArea(driver);

        if (viewport.getWidth() < width) {
            int delta = (int) (width - viewport.getWidth());
            driver.manage().window().setSize(new org.openqa.selenium.Dimension(width +  delta, height));
        }
    }
}
