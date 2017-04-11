/*
 * Copyright 2007-2013 The Europeana Foundation
 *
 *  Licenced under the EUPL, Version 1.1 (the "Licence") and subsequent versions as approved
 *  by the European Commission;
 *  You may not use this work except in compliance with the Licence.
 *
 *  You may obtain a copy of the Licence at:
 *  http://joinup.ec.europa.eu/software/page/eupl
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under
 *  the Licence is distributed on an "AS IS" basis, without warranties or conditions of
 *  any kind, either express or implied.
 *  See the Licence for the specific language governing permissions and limitations under
 *  the Licence.
 */
package eu.europeana.redirects.rest.config;

import com.mongodb.MongoClientURI;
import eu.europeana.corelib.lookup.impl.CollectionMongoServerImpl;
import eu.europeana.corelib.lookup.impl.EuropeanaIdMongoServerImpl;
import eu.europeana.corelib.storage.impl.MongoProviderImpl;
import eu.europeana.corelib.tools.lookuptable.CollectionMongoServer;
import eu.europeana.corelib.tools.lookuptable.EuropeanaIdMongoServer;
import eu.europeana.metis.utils.PivotalCloudFoundryServicesReader;
import eu.europeana.redirects.service.RedirectService;
import eu.europeana.redirects.service.mongo.MongoRedirectService;
import java.net.MalformedURLException;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.impl.CloudSolrServer;
import org.apache.solr.client.solrj.impl.LBHttpSolrServer;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@Configuration
@ComponentScan(basePackages = {"eu.europeana.redirects.rest"})
@PropertySource("classpath:redirects.properties")
@EnableWebMvc
@EnableSwagger2
public class Application extends WebMvcConfigurerAdapter implements InitializingBean{
    @Value("${mongo.hosts}")
    private String mongoHosts;
    @Value("${mongo.port}")
    private int mongoPort;
    @Value("${mongo.username}")
    private String mongoUsername;
    @Value("${mongo.password}")
    private String mongoPassword;
    @Value("${mongo.db}")
    private String mongoDb;
    @Value("${mongo.collections.db}")
    private String mongoCollectionsDb;

    @Value("${zookeeper.production}")
    private String zookeeperProduction;
    @Value("${solr.production}")
    private String solrProduction;
    @Value("${solr.production.core}")
    private String solrProductionCore;

    private MongoProviderImpl mongoProvider;
    private MongoProviderImpl mongoProviderCollections;

    /**
     * Used for overwriting properties if cloud foundry environment is used
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        String vcapServicesJson = System.getenv().get("VCAP_SERVICES");
        if (StringUtils.isNotEmpty(vcapServicesJson) && !StringUtils.equals(vcapServicesJson, "{}")) {
            PivotalCloudFoundryServicesReader vcapServices = new PivotalCloudFoundryServicesReader(
                vcapServicesJson);

            MongoClientURI mongoClientURI = vcapServices.getMongoClientUriFromService();
            String mongoHostAndPort = mongoClientURI.getHosts().get(0);
            mongoHosts = mongoHostAndPort.substring(0, mongoHostAndPort.lastIndexOf(":"));
            mongoPort = Integer
                .parseInt(mongoHostAndPort.substring(mongoHostAndPort.lastIndexOf(":") + 1));
            mongoUsername = mongoClientURI.getUsername();
            mongoPassword = String.valueOf(mongoClientURI.getPassword());
            mongoDb = mongoClientURI.getDatabase();
            mongoCollectionsDb = mongoClientURI.getDatabase();
        }

        String[] mongoHostsArray = mongoHosts.split(",");
        StringBuilder mongoPorts = new StringBuilder();
        for (int i = 0; i < mongoHostsArray.length; i++) {
            mongoPorts.append(mongoPort + ",");
        }
        mongoPorts.replace(mongoPorts.lastIndexOf(","), mongoPorts.lastIndexOf(","), "");
        mongoProvider = new MongoProviderImpl(mongoHosts, mongoPorts.toString(), mongoDb, mongoUsername,
            mongoPassword);
        mongoProviderCollections = new MongoProviderImpl(mongoHosts, mongoPorts.toString(), mongoCollectionsDb, mongoUsername,
            mongoPassword);
    }

    @Bean(name = "mongoServer")
    public EuropeanaIdMongoServer getMongoServer() {
        return new EuropeanaIdMongoServerImpl(mongoProvider.getMongo(), mongoDb);
    }

    @Bean(name = "collectionMongoServer")
    public CollectionMongoServer getCollectionMongoServer() {
        return new CollectionMongoServerImpl(mongoProviderCollections.getMongo(), mongoCollectionsDb);
    }

    @Bean(name = "productionSolrServer")
    public CloudSolrServer getProductionSolrServer() throws MalformedURLException {
        LBHttpSolrServer lbTargetProduction = new LBHttpSolrServer(solrProduction);
        CloudSolrServer productionSolrServer;
        productionSolrServer = new CloudSolrServer(zookeeperProduction, lbTargetProduction);
        productionSolrServer.setDefaultCollection(solrProductionCore);
        productionSolrServer.connect();

        return productionSolrServer;
    }

    @Bean
    @DependsOn(value = {"mongoServer", "collectionMongoServer", "productionSolrServer"})
    RedirectService getRedirectService(){
        return new MongoRedirectService();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("swagger-ui.html").addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
    }
    @Override
    public  void configureMessageConverters(List<HttpMessageConverter<?>> converters){
        converters.add(new MappingJackson2HttpMessageConverter());
        super.configureMessageConverters(converters);
    }

    @Bean
    public static PropertySourcesPlaceholderConfigurer properties() {
        PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer = new PropertySourcesPlaceholderConfigurer();
        return propertySourcesPlaceholderConfigurer;
    }

    @Bean
    public Docket api(){
        return new Docket(DocumentationType.SWAGGER_2)
                .select()
                .apis(RequestHandlerSelectors.any())
                .paths(PathSelectors.regex("/.*"))
                .build()
                .apiInfo(apiInfo());
    }

    private ApiInfo apiInfo() {
        ApiInfo apiInfo = new ApiInfo(
                "Metis redirects REST API",
                "Metis redirects REST API for Europeana",
                "v1",
                "API TOS",
                "development@europeana.eu",
                "EUPL Licence v1.1",
                ""
        );
        return apiInfo;
    }
}
