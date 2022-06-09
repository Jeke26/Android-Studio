/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tyron.builder.api.reporting.dependencies.internal;

import com.googlecode.jatl.Html;
import com.tyron.builder.api.BuildProject;
import com.tyron.builder.api.Transformer;
import com.tyron.builder.reporting.HtmlPageBuilder;
import com.tyron.builder.reporting.ReportRenderer;
import com.tyron.builder.util.GradleVersion;

import java.io.Writer;
import java.util.Date;

public class ProjectPageRenderer extends ReportRenderer<BuildProject, HtmlPageBuilder<Writer>> {
    private final Transformer<String, BuildProject> namingScheme;

    public ProjectPageRenderer(Transformer<String, BuildProject> namingScheme) {
        this.namingScheme = namingScheme;
    }

    @Override
    public void render(final BuildProject project, final HtmlPageBuilder<Writer> builder) {
        final String baseCssLink = requireClassResource("/org/gradle/reporting/base-style.css", builder);
        final String cssLink = requireReportResource("style.css", builder);
        final String jqueryLink = requireClassResource("/org/gradle/reporting/jquery.min-3.5.1.js", builder);
        final String jtreeLink = requireReportResource("jquery.jstree.js", builder);
        final String scriptLink = requireReportResource("script.js", builder);
        requireReportResource("tree.css", builder);
        requireReportResource("d.gif", builder);
        requireReportResource("d.png", builder);
        requireReportResource("throbber.gif", builder);

        new Html(builder.getOutput()) {{
            html();
                head();
                    meta().httpEquiv("Content-Type").content("text/html; charset=utf-8");
                    meta().httpEquiv("x-ua-compatible").content("IE=edge");
                    link().rel("stylesheet").type("text/css").href(baseCssLink).end();
                    link().rel("stylesheet").type("text/css").href(cssLink).end();
                    script().src(jqueryLink).charset("utf-8").end();
                    script().src(jtreeLink).charset("utf-8").end();
                    script().src(namingScheme.transform(project)).charset("utf-8").end();
                    script().src(scriptLink).charset("utf-8").end();
                    title().text("Dependency reports").end();
                end();
                body();
                    div().id("content");
                        h1().text("Dependency Report").end();
                        div().classAttr("breadcrumbs");
                            a().href("index.html").text("Projects").end();
                            text(" > ");
                            span().id("projectBreadcrumb").end();
                        end();
                        div().id("insight").end();
                        div().id("dependencies").end();
                        div().id("footer");
                            p();
                                text("Generated by ");
                                a().href("http://www.gradle.org").text(GradleVersion.current().toString()).end();
                                text(" at " + builder.formatDate(new Date()));
                            end();
                        end();
                    end();
                end();
            endAll();
        }};
    }

    private String requireReportResource(String path, HtmlPageBuilder<Writer> builder) {
        return requireClassResource(getReportResourcePath(path), builder);
    }

    private String requireClassResource(String path, HtmlPageBuilder<Writer> builder) {
        return builder.requireResource(getClass().getResource(path));
    }

    private String getReportResourcePath(String fileName) {
        return "/org/gradle/api/tasks/diagnostics/htmldependencyreport/" + fileName;
    }
}
