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
package com.galenframework.tests.api;

import com.galenframework.api.GalenPageDump;
import com.galenframework.page.Rect;
import com.galenframework.specs.reader.page.SectionFilter;
import com.google.common.io.Files;
import com.google.gson.JsonParser;

import com.galenframework.api.Galen;
import com.galenframework.components.mocks.driver.MockedDriver;
import com.galenframework.reports.model.LayoutReport;
import com.galenframework.validation.ValidationObject;
import com.galenframework.validation.ValidationError;

import com.galenframework.validation.ValidationResult;
import org.junit.Assert;
import org.openqa.selenium.WebDriver;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import static java.util.Arrays.asList;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class GalenTest {

    @Test
    public void checkLayout_shouldTestLayout_andReturnLayoutReport() throws IOException {
        WebDriver driver = new MockedDriver();
        driver.get("/mocks/pages/galen4j-sample-page.json");

        LayoutReport layoutReport = Galen.checkLayout(driver, "/specs/galen4j/sample-spec-with-error.spec", new SectionFilter(asList("mobile"), null), new Properties(), null, null);

        assertThat(layoutReport.getValidationErrorResults(), contains(
                new ValidationResult(
                        asList(
                                new ValidationObject(new Rect(10, 10, 100, 50), "save-button"),
                                new ValidationObject(new Rect(120, 10, 200, 50), "name-textfield")),
                        new ValidationError().withMessage("\"save-button\" is 10px left instead of 50px")),
                new ValidationResult(
                        asList(
                                new ValidationObject(new Rect(10, 10, 100, 50), "save-button")),
                        new ValidationError().withMessage("\"save-button\" text is \"Save\" but should be \"Store\""))));
    }


    @Test
    public void dumpPage_shouldGenereate_htmlJsonReport_andStorePicturesOfElements() throws IOException {
        String pageDumpPath = Files.createTempDir().getAbsolutePath() + "/pagedump";

        WebDriver driver = new MockedDriver();
        driver.get("/mocks/pages/galen4j-pagedump.json");
        new GalenPageDump("test page").dumpPage(driver, "/specs/galen4j/pagedump.spec", pageDumpPath);

        assertFileExists(pageDumpPath + "/page.json");
        assertJSONContent(pageDumpPath + "/page.json", "/pagedump/expected.json");
        assertFileExists(pageDumpPath + "/page.html");

        assertFileExists(pageDumpPath + "/page.png");
        assertFileExists(pageDumpPath + "/objects/button-save.png");
        assertFileExists(pageDumpPath + "/objects/name-textfield.png");
        assertFileExists(pageDumpPath + "/objects/menu-item-1.png");
        assertFileExists(pageDumpPath + "/objects/menu-item-2.png");
        assertFileExists(pageDumpPath + "/objects/menu-item-3.png");
        assertFileExists(pageDumpPath + "/objects/big-container.png");

        assertFileExists(pageDumpPath + "/jquery-1.11.2.min.js");
        assertFileExists(pageDumpPath + "/galen-pagedump.js");
        assertFileExists(pageDumpPath + "/galen-pagedump.css");
    }


    @Test
    public void dumpPage_shouldOnlyStoreScreenshots_thatAreLessThan_theMaxAllowed() throws IOException {
        String pageDumpPath = Files.createTempDir().getAbsolutePath() + "/pagedump";

        WebDriver driver = new MockedDriver();
        driver.get("/mocks/pages/galen4j-pagedump.json");
        new GalenPageDump("test page")
                .setMaxWidth(80)
                .setMaxHeight(80)
                .dumpPage(driver, "/specs/galen4j/pagedump.spec", pageDumpPath);

        assertFileExists(pageDumpPath + "/objects/button-save.png");
        assertFileDoesNotExist(pageDumpPath + "/objects/name-textfield.png");
        assertFileExists(pageDumpPath + "/objects/menu-item-1.png");
        assertFileExists(pageDumpPath + "/objects/menu-item-2.png");
        assertFileExists(pageDumpPath + "/objects/menu-item-3.png");
        assertFileDoesNotExist(pageDumpPath + "/objects/big-container.png");


        assertFileExists(pageDumpPath + "/page.json");
        assertFileExists(pageDumpPath + "/page.html");
        assertFileExists(pageDumpPath + "/jquery-1.11.2.min.js");
        assertFileExists(pageDumpPath + "/galen-pagedump.js");
        assertFileExists(pageDumpPath + "/galen-pagedump.css");
    }

    @Test
    public void dumpPage_shouldOnlyStoreScreenshots_withoutHtmlReport() throws IOException {
        String pageDumpPath = Files.createTempDir().getAbsolutePath() + "/pagedump";

        WebDriver driver = new MockedDriver();
        driver.get("/mocks/pages/galen4j-pagedump.json");
        new GalenPageDump("test page")
                .setMaxWidth(80)
                .setMaxHeight(80)
                .setOnlyImages(true)
                .dumpPage(driver, "/specs/galen4j/pagedump.spec", pageDumpPath);

        assertFileExists(pageDumpPath + "/objects/button-save.png");
        assertFileDoesNotExist(pageDumpPath + "/objects/name-textfield.png");
        assertFileExists(pageDumpPath + "/objects/menu-item-1.png");
        assertFileExists(pageDumpPath + "/objects/menu-item-2.png");
        assertFileExists(pageDumpPath + "/objects/menu-item-3.png");
        assertFileDoesNotExist(pageDumpPath + "/objects/big-container.png");

        assertFileDoesNotExist(pageDumpPath + "/page.json");
        assertFileDoesNotExist(pageDumpPath + "/page.html");
        assertFileDoesNotExist(pageDumpPath + "/jquery-1.11.2.min.js");
        assertFileDoesNotExist(pageDumpPath + "/galen-pagedump.js");
        assertFileDoesNotExist(pageDumpPath + "/galen-pagedump.css");
    }

    @Test
    public void dumpPage_shouldExcludeObjects_thatMatch_givenRegex() throws IOException {
        String pageDumpPath = Files.createTempDir().getAbsolutePath() + "/pagedump";

        WebDriver driver = new MockedDriver();
        driver.get("/mocks/pages/galen4j-pagedump.json");
        new GalenPageDump("test page")
                .setExcludedObjects(asList(
                    "big-container",
                    "menu-item-#"))
                .dumpPage(driver,  "/specs/galen4j/pagedump.spec", pageDumpPath);

        assertFileExists(pageDumpPath + "/page.json");
        assertJSONContent(pageDumpPath + "/page.json", "/pagedump/expected-without-excluded-objects.json");
        assertFileExists(pageDumpPath + "/page.html");

        assertFileExists(pageDumpPath + "/page.png");
        assertFileExists(pageDumpPath + "/objects/button-save.png");
        assertFileExists(pageDumpPath + "/objects/name-textfield.png");
        assertFileDoesNotExist(pageDumpPath + "/objects/menu-item-1.png");
        assertFileDoesNotExist(pageDumpPath + "/objects/menu-item-2.png");
        assertFileDoesNotExist(pageDumpPath + "/objects/menu-item-3.png");
        assertFileDoesNotExist(pageDumpPath + "/objects/big-container.png");

        assertFileExists(pageDumpPath + "/jquery-1.11.2.min.js");
        assertFileExists(pageDumpPath + "/galen-pagedump.js");
        assertFileExists(pageDumpPath + "/galen-pagedump.css");

    }

    /**
     * comes from https://github.com/galenframework/galen/issues/324
     */
    @Test
    public void checkLayout_shouldGiveErrors_ifCustomRules_areFailed() throws IOException {
        WebDriver driver = new MockedDriver();
        driver.get("/mocks/pages/galen4j-sample-page.json");

        LayoutReport layoutReport = Galen.checkLayout(driver, "/specs/galen4j/custom-rules-failure.spec", new SectionFilter(null, null), new Properties(), null, null);

        assertThat(layoutReport.errors(), is(2));
        assertThat(layoutReport.getValidationErrorResults(), contains(
                new ValidationResult(
                        asList(
                                new ValidationObject(new Rect(10, 10, 100, 50), "save-button")),
                        new ValidationError().withMessage("\"save-button\" width is 100px instead of 140px")),
                new ValidationResult(
                        asList(
                                new ValidationObject(new Rect(10, 10, 100, 50), "save-button")),
                        new ValidationError().withMessage("\"save-button\" width is 200% [100px] instead of 100% [50px]"))));
    }

    private void assertJSONContent(String pathForRealContent, String pathForExpectedContent) throws IOException {
        Assert.assertEquals(String.format("Content of \"%s\" should be the same as in \"%s\"", pathForRealContent, pathForExpectedContent),
                new JsonParser().parse(readFileToString(new File(pathForRealContent)).replaceAll("\\s+", "")),
                        new JsonParser().parse(readFileToString(new File(getClass().getResource(pathForExpectedContent).getFile())).replaceAll("\\s+", "")));
    }

    private void assertFileDoesNotExist(String path) {
        assertThat("File " + path + " + should not exist", new File(path).exists(), is(false));
    }

    private void assertFileExists(String path) {
        assertThat("File " + path + " should exist", new File(path).exists(), is(true));
    }

}
