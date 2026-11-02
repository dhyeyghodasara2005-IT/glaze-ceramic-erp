package com.ceramicerp.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.time.Duration;

/**
 * Serves the React app from inside the jar, so the plant runs one program instead of two. Only a release build has
 * these files (see the "release" profile in pom.xml); in development Vite serves them and this finds nothing.
 */
@Configuration
public class SinglePageAppConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Vite puts a hash in these names, so a new version has a new name: browsers may keep them for good.
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());

        // index.html keeps its name, so browsers must ask every time. That is how an upgrade reaches every phone.
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new AppPageResolver());
    }

    /**
     * /store/lots isn't a file: it is a page React draws in the browser. So when someone reloads it or opens a link,
     * the server answers with index.html and React takes over from the address.
     */
    static class AppPageResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String path, Resource location) throws IOException {
            Resource file = super.getResource(path, location);
            if (file != null) {
                return file;
            }
            return isPage(path) ? super.getResource("index.html", location) : null;
        }

        /** A wrong API address must stay an error, and so must a missing file: only real page addresses get the app. */
        static boolean isPage(String path) {
            return !path.startsWith("api/") && !path.startsWith("actuator/") && !path.startsWith("v3/") && !path.contains(".");
        }
    }
}
