/*
 * Copyright (c) 2011 David Boissier
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.codinjutsu.tools.jenkins.security;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;


class BasicSecurityClient implements SecurityClient {

    private URL master;

    private final String username;
    private final String passwordFile;
    private String password = null;

    private final HttpClient client;


    BasicSecurityClient(String username, String passwordFile) {
        this.client = new HttpClient();
        this.username = username;
        this.passwordFile = passwordFile;
    }


    public void connect(URL jenkinsUrl) throws Exception {
        master = jenkinsUrl;

        if (passwordFile != null) {
            password = IOUtils.toString(new FileInputStream(passwordFile));
            if (StringUtils.isNotEmpty(password)) {
                password = StringUtils.removeEnd(password, "\n");
            }
        }

        if (password == null && username == null) {
            checkJenkinsSecurity();
        }
        doAuthentication();
    }


    public String execute(URL url) throws Exception {
        PostMethod post = new PostMethod(url.toString());
        try {
            client.executeMethod(post);
            checkStatusCode(post.getStatusCode());
            return post.getResponseBodyAsString();
        } finally {
            post.releaseConnection();
        }
    }

    private static void checkStatusCode(int statusCode) throws AuthenticationException {
        if (HttpURLConnection.HTTP_FORBIDDEN == statusCode) {
            throw new AuthenticationException("Forbidden");
        }
        if (HttpURLConnection.HTTP_INTERNAL_ERROR == statusCode) {
            throw new AuthenticationException("Server Internal Error");
        }
    }

    public InputStream executeAndGetResponseStream(URL url) throws Exception {
        PostMethod post = new PostMethod(url.toString());
        client.executeMethod(post);
        checkStatusCode(post.getStatusCode());
        return post.getResponseBodyAsStream();
    }


    private void doAuthentication() throws IOException, AuthenticationException {

        if (username != null && password != null) {
            client.getState().setCredentials(
                    new AuthScope(master.getHost(), master.getPort()),
                    new UsernamePasswordCredentials(username, password));
        }


        client.getParams().setAuthenticationPreemptive(true);
        client.getParams().setParameter("http.protocol.handle-redirects", false);

        PostMethod postMethod = new PostMethod(master.toString());


        postMethod.setDoAuthentication(true);
        postMethod.setFollowRedirects(false);
        int responseCode = client.executeMethod(postMethod);
        if (responseCode != HttpURLConnection.HTTP_OK) {
            if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED || responseCode == HttpURLConnection.HTTP_FORBIDDEN
                    || responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                throw new AuthenticationException("Bad Credentials.");
            }
        }

        postMethod.releaseConnection();
    }


    private void checkJenkinsSecurity() throws AuthenticationException {
        try {
            HttpURLConnection con = (HttpURLConnection) master
                    .openConnection();
            con.connect();

            if (con.getResponseCode() == HttpURLConnection.HTTP_FORBIDDEN) {
                throw new AuthenticationException("This Jenkins server requires authentication!");
            }

            String jenkinsHeader = con.getHeaderField("X-Jenkins");
            if (jenkinsHeader == null) {
                throw new AuthenticationException("This URL doesn't look like Jenkins.");
            }
        } catch (IOException ioEx) {
            throw new AuthenticationException("Failed to connect to " + master, ioEx);
        }
    }


}
