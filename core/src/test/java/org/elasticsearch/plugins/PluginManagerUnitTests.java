/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.plugins;

import com.google.common.io.Files;
import org.elasticsearch.Build;
import org.elasticsearch.Version;
import org.elasticsearch.common.http.client.HttpDownloadHelper;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.test.ESTestCase;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;

import static org.elasticsearch.common.settings.Settings.settingsBuilder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

/**
 *
 */
public class PluginManagerUnitTests extends ESTestCase {

    @After
    public void cleanSystemProperty() {
        System.clearProperty(PluginManager.PROPERTY_SUPPORT_STAGING_URLS);
    }

    @Test
    public void testThatConfigDirectoryCanBeOutsideOfElasticsearchHomeDirectory() throws IOException {
        String pluginName = randomAsciiOfLength(10);
        Path homeFolder = createTempDir();
        Path genericConfigFolder = createTempDir();

        Settings settings = settingsBuilder()
                .put("path.conf", genericConfigFolder)
                .put("path.home", homeFolder)
                .build();
        Environment environment = new Environment(settings);

        PluginManager.PluginHandle pluginHandle = new PluginManager.PluginHandle(pluginName, "version", "user", "repo");
        String configDirPath = Files.simplifyPath(pluginHandle.configDir(environment).normalize().toString());
        String expectedDirPath = Files.simplifyPath(genericConfigFolder.resolve(pluginName).normalize().toString());

        assertThat(configDirPath, is(expectedDirPath));
    }

    @Test
    public void testSimplifiedNaming() throws IOException {
        String pluginName = randomAsciiOfLength(10);
        PluginManager.PluginHandle handle = PluginManager.PluginHandle.parse(pluginName);

        boolean supportStagingUrls = randomBoolean();
        if (supportStagingUrls) {
            System.setProperty(PluginManager.PROPERTY_SUPPORT_STAGING_URLS, "true");
        }

        Iterator<URL> iterator = handle.urls().iterator();

        if (supportStagingUrls) {
            String expectedStagingURL = String.format(Locale.ROOT, "http://download.elastic.co/elasticsearch/staging/%s/org/elasticsearch/plugin/elasticsearch-%s/%s/elasticsearch-%s-%s.zip",
                    Build.CURRENT.hashShort(), pluginName, Version.CURRENT.number(), pluginName, Version.CURRENT.number());
            assertThat(iterator.next(), is(new URL(expectedStagingURL)));
        }

        URL expected = new URL("http", "download.elastic.co", "/elasticsearch/release/org/elasticsearch/plugin/elasticsearch-" + pluginName + "/" + Version.CURRENT.number() + "/elasticsearch-" +
                pluginName + "-" + Version.CURRENT.number() + ".zip");
        assertThat(iterator.next(), is(expected));

        assertThat(iterator.hasNext(), is(false));
    }

    @Test
    public void testTrimmingElasticsearchFromOfficialPluginName() throws IOException {
        String randomPluginName = randomFrom(PluginManager.OFFICIAL_PLUGINS.asList()).replaceFirst("elasticsearch-", "");
        PluginManager.PluginHandle handle = PluginManager.PluginHandle.parse(randomPluginName);
        assertThat(handle.name, is(randomPluginName.replaceAll("^elasticsearch-", "")));

        boolean supportStagingUrls = randomBoolean();
        if (supportStagingUrls) {
            System.setProperty(PluginManager.PROPERTY_SUPPORT_STAGING_URLS, "true");
        }

        Iterator<URL> iterator = handle.urls().iterator();

        if (supportStagingUrls) {
            String expectedStagingUrl = String.format(Locale.ROOT, "http://download.elastic.co/elasticsearch/staging/%s/org/elasticsearch/plugin/elasticsearch-%s/%s/elasticsearch-%s-%s.zip",
                    Build.CURRENT.hashShort(), randomPluginName, Version.CURRENT.number(), randomPluginName, Version.CURRENT.number());
            assertThat(iterator.next(), is(new URL(expectedStagingUrl)));
        }

        String releaseUrl = String.format(Locale.ROOT, "http://download.elastic.co/elasticsearch/release/org/elasticsearch/plugin/elasticsearch-%s/%s/elasticsearch-%s-%s.zip",
                randomPluginName, Version.CURRENT.number(), randomPluginName, Version.CURRENT.number());
        assertThat(iterator.next(), is(new URL(releaseUrl)));

        assertThat(iterator.hasNext(), is(false));
    }

    @Test
    public void testTrimmingElasticsearchFromGithubPluginName() throws IOException {
        String user = randomAsciiOfLength(6);
        String randomName = randomAsciiOfLength(10);
        String pluginName = randomFrom("elasticsearch-", "es-") + randomName;
        PluginManager.PluginHandle handle = PluginManager.PluginHandle.parse(user + "/" + pluginName);
        assertThat(handle.name, is(randomName));
        assertThat(handle.urls(), hasSize(1));
        URL expected = new URL("https", "github.com", "/" + user + "/" + pluginName + "/" + "archive/master.zip");
        assertThat(handle.urls().get(0), is(expected));
    }

    @Test
    public void testDownloadHelperChecksums() throws Exception {
        // Sanity check to make sure the checksum functions never change how they checksum things
        assertEquals("0beec7b5ea3f0fdbc95d0dd47f3c5bc275da8a33",
                HttpDownloadHelper.SHA1_CHECKSUM.checksum("foo".getBytes(Charset.forName("UTF-8"))));
        assertEquals("acbd18db4cc2f85cedef654fccc4a4d8",
                HttpDownloadHelper.MD5_CHECKSUM.checksum("foo".getBytes(Charset.forName("UTF-8"))));
    }
}
