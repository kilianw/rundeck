/*
 * Copyright 2016 SimplifyOps, Inc. (http://simplifyops.com)
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

/*
* PluginManagerService.java
* 
* User: Greg Schueler <a href="mailto:greg@dtosolutions.com">greg@dtosolutions.com</a>
* Created: 3/31/11 2:00 PM
* 
*/
package com.dtolabs.rundeck.core.plugins;

import com.dtolabs.rundeck.core.common.Framework;
import com.dtolabs.rundeck.core.common.FrameworkSupportService;
import com.dtolabs.rundeck.core.execution.service.MissingProviderException;
import com.dtolabs.rundeck.core.execution.service.ProviderLoaderException;
import com.dtolabs.rundeck.core.utils.cache.FileCache;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PluginManagerService is ...
 *
 * @author Greg Schueler <a href="mailto:greg@dtosolutions.com">greg@dtosolutions.com</a>
 */
public class PluginManagerService implements FrameworkSupportService, ServiceProviderLoader {
    private static final Logger log = Logger.getLogger(PluginManagerService.class.getName());
    public static final String SERVICE_NAME = "PluginManager";

    private File extdir;
    private File cachedir;
    private  PluginCache cache;
    private Map<String, String> serviceAliases;
    /**
     * Create a PluginManagerService
     */
    public PluginManagerService() {

    }

    /**
     * Create
     * @param type
     * @param serviceName
     * @param <T>
     * @return
     */
    @Override
    public <T> PluggableProviderService<T> createPluginService(Class<T> type, final String serviceName) {
        BasePluginProviderService<T> basePluginProviderService = new BasePluginProviderService<T>(
                serviceName,
                type
        );
        basePluginProviderService.setRundeckServerServiceProviderLoader(this);
        return basePluginProviderService;
    }


    /**
     * Create a PluginManagerService for the given directory and cache directory
     * @param extdir plugin dir
     * @param cachedir cache dir
     */
    public PluginManagerService(final File extdir, final File cachedir, final PluginCache cache) {
        this.setExtdir(extdir);
        this.setCachedir(cachedir);
        this.setCache(cache);
        log.debug("Create PluginManagerService with cache: "+cache);
    }

    public static FileCache<ProviderLoader> createProviderLoaderFileCache() {
        return new FileCache<ProviderLoader>();
    }


    public String getName() {
        return SERVICE_NAME;
    }

    public static PluginManagerService getInstanceForFramework(final Framework framework) {
        return (PluginManagerService) framework.getService(SERVICE_NAME);
    }

    public synchronized List<ProviderIdent> listProviders() {
        return getCache().listProviders();
    }

    @Override
    public <T> CloseableProvider<T> loadCloseableProvider(
            final PluggableService<T> service,
            final String providerName
    ) throws ProviderLoaderException
    {
        final ProviderIdent ident = new ProviderIdent(service.getName(), providerName);
        final ProviderLoader loaderForIdent = getCache().getLoaderForIdent(ident);
        if (null == loaderForIdent) {
            throw new MissingProviderException("No matching plugin found", service.getName(), providerName);
        }
        if (service.canLoadWithLoader(loaderForIdent)) {
            final CloseableProvider<T> load = service.loadCloseableWithLoader(providerName, loaderForIdent);
            if (null != load) {
                return load;
            }
        }
        throw new ProviderLoaderException(
                "Unable to load provider: " + providerName + ", for service: " + service.getName(),
                service.getName(),
                providerName
        );

    }

    public synchronized <T> T loadProvider(final PluggableService<T> service, final String providerName) throws ProviderLoaderException {
        final ProviderIdent ident = new ProviderIdent(service.getName(), providerName);
        final ProviderLoader loaderForIdent = getCache().getLoaderForIdent(ident);
        if (null == loaderForIdent) {
            throw new MissingProviderException("No matching plugin found", service.getName(), providerName);
        }
        if (service.canLoadWithLoader(loaderForIdent)) {
            final T load = service.loadWithLoader(providerName, loaderForIdent);
            if (null != load) {
                return load;
            }
        }
        throw new ProviderLoaderException(
            "Unable to load provider: " + providerName + ", for service: " + service.getName(), service.getName(),
            providerName
        );

    }

    @Override
    public PluginResourceLoader getResourceLoader(String service, String provider) throws ProviderLoaderException {
        PluginResourceLoader pluginResourceLoader = tryResourceLoader(service, provider);
        if (pluginResourceLoader == null && serviceAliases != null && serviceAliases.containsKey(service)) {
            pluginResourceLoader = tryResourceLoader(serviceAliases.get(service), provider);
        }
        return pluginResourceLoader;
    }

    private PluginResourceLoader tryResourceLoader(String service, String provider) throws ProviderLoaderException {
        ProviderLoader loaderForIdent = getCache().getLoaderForIdent(new ProviderIdent(service, provider));
        if (null != loaderForIdent && loaderForIdent instanceof PluginResourceLoader) {
            return (PluginResourceLoader) loaderForIdent;
        }
        return null;
    }

    @Override
    public PluginMetadata getPluginMetadata(final String service, final String provider)
            throws ProviderLoaderException
    {
        PluginMetadata pluginMetadata = tryPluginMetadata(service, provider);
        if (pluginMetadata == null && serviceAliases != null && serviceAliases.containsKey(service)) {
            pluginMetadata = tryPluginMetadata(serviceAliases.get(service), provider);
        }
        return pluginMetadata;
    }

    private PluginMetadata tryPluginMetadata(String service, final String provider)
            throws ProviderLoaderException
    {
        ProviderLoader loaderForIdent = getCache().getLoaderForIdent(new ProviderIdent(service, provider));
        if (null != loaderForIdent && loaderForIdent instanceof PluginMetadata) {
            return (PluginMetadata) loaderForIdent;
        }
        return null;
    }

    public File getExtdir() {
        return extdir;
    }

    public void setExtdir(File extdir) {
        this.extdir = extdir;
    }

    public File getCachedir() {
        return cachedir;
    }

    public void setCachedir(File cachedir) {
        this.cachedir = cachedir;
    }

    public PluginCache getCache() {
        return cache;
    }

    public void setCache(PluginCache cache) {
        this.cache = cache;
    }

    public Map<String, String> getServiceAliases() {
        return serviceAliases;
    }

    public void setServiceAliases(Map<String, String> serviceAliases) {
        this.serviceAliases = serviceAliases;
    }
}
