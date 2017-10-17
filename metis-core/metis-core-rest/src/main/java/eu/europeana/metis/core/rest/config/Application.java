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
package eu.europeana.metis.core.rest.config;


import com.mongodb.MongoClientOptions;
import com.mongodb.MongoClientURI;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import eu.europeana.cloud.mcs.driver.DataSetServiceClient;
import eu.europeana.corelib.storage.impl.MongoProviderImpl;
import eu.europeana.corelib.web.socks.SocksProxy;
import eu.europeana.metis.cache.redis.JedisProviderUtils;
import eu.europeana.metis.cache.redis.RedisProvider;
import eu.europeana.metis.core.api.MetisKey;
import eu.europeana.metis.core.dao.AuthorizationDao;
import eu.europeana.metis.core.dao.DatasetDao;
import eu.europeana.metis.core.dao.OrganizationDao;
import eu.europeana.metis.core.dao.ScheduledUserWorkflowDao;
import eu.europeana.metis.core.dao.UserWorkflowDao;
import eu.europeana.metis.core.dao.UserWorkflowExecutionDao;
import eu.europeana.metis.core.dao.ZohoClient;
import eu.europeana.metis.core.dao.ecloud.EcloudDatasetDao;
import eu.europeana.metis.core.execution.FailsafeExecutor;
import eu.europeana.metis.core.execution.SchedulerExecutor;
import eu.europeana.metis.core.execution.UserWorkflowExecutorManager;
import eu.europeana.metis.core.mail.config.MailConfig;
import eu.europeana.metis.core.mongo.MorphiaDatastoreProvider;
import eu.europeana.metis.core.rest.RequestLimits;
import eu.europeana.metis.core.search.config.SearchApplication;
import eu.europeana.metis.core.search.service.MetisSearchService;
import eu.europeana.metis.core.service.CrmUserService;
import eu.europeana.metis.core.service.DatasetService;
import eu.europeana.metis.core.service.MetisAuthorizationService;
import eu.europeana.metis.core.service.OrchestratorService;
import eu.europeana.metis.core.service.OrganizationService;
import eu.europeana.metis.json.CustomObjectMapper;
import eu.europeana.metis.utils.PivotalCloudFoundryServicesReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import javax.annotation.PreDestroy;
import org.apache.commons.lang.StringUtils;
import org.bson.types.ObjectId;
import org.mongodb.morphia.Morphia;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.servlet.view.BeanNameViewResolver;
import org.springframework.web.servlet.view.json.MappingJackson2JsonView;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

/**
 * Spring configuration class Created by ymamakis on 12-2-16.
 */
@Configuration
@ComponentScan(basePackages = {"eu.europeana.metis.core.rest"})
@PropertySource({"classpath:metis.properties", "classpath:ecloud.properties"})
@EnableWebMvc
@EnableSwagger2
@Import({MailConfig.class, SearchApplication.class})
public class Application extends WebMvcConfigurerAdapter implements InitializingBean {

  //Orchestration
  @Value("${max.concurrent.threads}")
  private int maxConcurrentThreads;
  @Value("${monitor.check.interval.in.secs}")
  private int monitorCheckIntervalInSecs;
  @Value("${periodic.failsafe.check.in.secs}")
  private int periodicFailsafeCheckInSecs;
  @Value("${periodic.scheduler.check.in.secs}")
  private int periodicSchedulerCheckInSecs;

  //Socks proxy
  @Value("${socks.proxy.enabled}")
  private boolean socksProxyEnabled;
  @Value("${socks.proxy.host}")
  private String socksProxyHost;
  @Value("${socks.proxy.port}")
  private String socksProxyPort;
  @Value("${socks.proxy.username}")
  private String socksProxyUsername;
  @Value("${socks.proxy.password}")
  private String socksProxyPassword;

  //RabbitMq
  @Value("${rabbitmq.host}")
  private String rabbitmqHost;
  @Value("${rabbitmq.port}")
  private int rabbitmqPort;
  @Value("${rabbitmq.username}")
  private String rabbitmqUsername;
  @Value("${rabbitmq.password}")
  private String rabbitmqPassword;
  @Value("${rabbitmq.queue.name}")
  private String rabbitmqQueueName;
  @Value("${rabbitmq.highest.priority}")
  private int rabbitmqHighestPriority;

  //Redis
  @Value("${redis.host}")
  private String redisHost;
  @Value("${redis.port}")
  private int redisPort;
  @Value("${redis.password}")
  private String redisPassword;
  @Value("${redisson.lock.watchdog.timeout.in.secs}")
  private int redissonLockWatchdogTimeoutInSecs;

  //Mongo
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

  //Ecloud
  @Value("${ecloud.baseMcsUrl}")
  private String ecloudBaseMcsUrl;
  @Value("${ecloud.username}")
  private String ecloudUsername;
  @Value("${ecloud.password}")
  private String ecloudPassword;

  private MongoProviderImpl mongoProvider;
  private RedisProvider redisProvider;
  private Connection connection;
  private Channel channel;

  @Autowired
  private ZohoRestConfig zohoRestConfig;

  /**
   * Used for overwriting properties if cloud foundry environment is used
   */
  @Override
  public void afterPropertiesSet() throws Exception {
    if (socksProxyEnabled) {
      new SocksProxy(socksProxyHost, socksProxyPort, socksProxyUsername, socksProxyPassword).init();
    }

    String vcapServicesJson = System.getenv().get("VCAP_SERVICES");
    if (StringUtils.isNotEmpty(vcapServicesJson) && !StringUtils.equals(vcapServicesJson, "{}")) {
      PivotalCloudFoundryServicesReader vcapServices = new PivotalCloudFoundryServicesReader(
          vcapServicesJson);

      MongoClientURI mongoClientURI = vcapServices.getMongoClientUriFromService();
      if (mongoClientURI != null) {
        String mongoHostAndPort = mongoClientURI.getHosts().get(0);
        mongoHosts = mongoHostAndPort.substring(0, mongoHostAndPort.lastIndexOf(':'));
        mongoPort = Integer
            .parseInt(mongoHostAndPort.substring(mongoHostAndPort.lastIndexOf(':') + 1));
        mongoUsername = mongoClientURI.getUsername();
        mongoPassword = String.valueOf(mongoClientURI.getPassword());
        mongoDb = mongoClientURI.getDatabase();
      }

      RedisProvider redisProviderFromService = vcapServices.getRedisProviderFromService();
      if (redisProviderFromService != null) {
        redisProvider = vcapServices.getRedisProviderFromService();
      }
    }

    String[] mongoHostsArray = mongoHosts.split(",");
    StringBuilder mongoPorts = new StringBuilder();
    for (String aMongoHostsArray : mongoHostsArray) {
      mongoPorts.append(mongoPort).append(",");
    }
    mongoPorts.replace(mongoPorts.lastIndexOf(","), mongoPorts.lastIndexOf(","), "");
    MongoClientOptions.Builder options = MongoClientOptions.builder();
    options.socketKeepAlive(true);
    mongoProvider = new MongoProviderImpl(mongoHosts, mongoPorts.toString(), mongoDb, mongoUsername,
        mongoPassword, options);

    if (redisProvider == null) {
      redisProvider = new RedisProvider(redisHost, redisPort, redisPassword);
    }
  }

  @Bean
  MorphiaDatastoreProvider getMorphiaDatastoreProvider() {
    return new MorphiaDatastoreProvider(mongoProvider.getMongo(), mongoDb);
  }

  @Bean
  Channel getRabbitmqChannel() throws IOException, TimeoutException {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(rabbitmqHost);
    factory.setPort(rabbitmqPort);
    factory.setUsername(rabbitmqUsername);
    factory.setPassword(rabbitmqPassword);
    factory.setAutomaticRecoveryEnabled(true);
    connection = factory.newConnection();
    channel = connection.createChannel();
    Map<String, Object> args = new HashMap<>();
    args.put("x-max-priority", rabbitmqHighestPriority);//Higher number means higher priority
    //Second boolean durable to false
    channel.queueDeclare(rabbitmqQueueName, false, false, false, args);
    return channel;
  }

  @Bean
  ZohoClient getZohoRestClient() {
    return zohoRestConfig.getZohoClient();
  }

  @Bean(name = "jedisProviderUtils")
  JedisProviderUtils getJedisProviderUtils() {
    return new JedisProviderUtils(redisProvider.getJedis());
  }

  @Bean
  RedissonClient getRedissonClient() {
    Config config = new Config();
    config.useSingleServer().setAddress(String.format("redis://%s:%s", redisHost, redisPort));
    config.setLockWatchdogTimeout(
        redissonLockWatchdogTimeoutInSecs * 1000L); //Give some secs to unlock if connection lost, or if too long to unlock
    return Redisson.create(config);
  }

  @Bean
  public View json() {
    MappingJackson2JsonView view = new MappingJackson2JsonView();
    view.setPrettyPrint(true);
    view.setObjectMapper(new CustomObjectMapper());
    return view;
  }

  @Bean
  public ViewResolver viewResolver() {
    return new BeanNameViewResolver();
  }

  @Bean
  public AuthorizationDao getAuthorizationDao() {
    Morphia morphia = new Morphia();
    morphia.map(MetisKey.class);
    return new AuthorizationDao();
  }

  @Bean
  public DatasetDao getDatasetDao(MorphiaDatastoreProvider morphiaDatastoreProvider) {
    DatasetDao datasetDao = new DatasetDao(morphiaDatastoreProvider);
    datasetDao.setDatasetsPerRequest(RequestLimits.DATASETS_PER_REQUEST.getLimit());
    return datasetDao;
  }

  @Bean // Only used for starting the threaded class
  public FailsafeExecutor startFailsafeExecutorThread(OrchestratorService orchestratorService,
      RedissonClient redissonClient) {
    FailsafeExecutor failsafeExecutor = new FailsafeExecutor(orchestratorService, redissonClient,
        periodicFailsafeCheckInSecs, true);
    new Thread(failsafeExecutor).start();
    return failsafeExecutor;
  }

  @Bean // Only used for starting the threaded class
  public SchedulerExecutor startSchedulingExecutorThread(
      OrchestratorService orchestratorService, RedissonClient redissonClient) {
    SchedulerExecutor schedulerExecutor = new SchedulerExecutor(orchestratorService, redissonClient,
        periodicSchedulerCheckInSecs, true);
    new Thread(schedulerExecutor).start();
    return schedulerExecutor;
  }

  @Bean
  public UserWorkflowExecutionDao getUserWorkflowExecutionDao(
      MorphiaDatastoreProvider morphiaDatastoreProvider) {
    UserWorkflowExecutionDao userWorkflowExecutionDao = new UserWorkflowExecutionDao(
        morphiaDatastoreProvider);
    userWorkflowExecutionDao.setUserWorkflowExecutionsPerRequest(
        RequestLimits.USER_WORKFLOW_EXECUTIONS_PER_REQUEST.getLimit());
    return userWorkflowExecutionDao;
  }

  @Bean
  public ScheduledUserWorkflowDao getScheduledUserWorkflowDao(
      MorphiaDatastoreProvider morphiaDatastoreProvider) {
    return new ScheduledUserWorkflowDao(morphiaDatastoreProvider);
  }

  @Bean
  public UserWorkflowDao getUserWorkflowDao(MorphiaDatastoreProvider morphiaDatastoreProvider) {
    UserWorkflowDao userWorkflowDao = new UserWorkflowDao(morphiaDatastoreProvider);
    userWorkflowDao.setUserWorkflowsPerRequest(RequestLimits.USER_WORKFLOWS_PER_REQUEST.getLimit());
    return userWorkflowDao;
  }

  @Bean
  DataSetServiceClient dataSetServiceClient() {
    return new DataSetServiceClient(ecloudBaseMcsUrl, ecloudUsername, ecloudPassword);
  }

  @Bean
  EcloudDatasetDao ecloudDatasetDao(DataSetServiceClient dataSetServiceClient) {
    return new EcloudDatasetDao(dataSetServiceClient);
  }

  @Bean
  public OrganizationDao getOrganizationDao(MorphiaDatastoreProvider morphiaDatastoreProvider) {
    OrganizationDao organizationDao = new OrganizationDao(morphiaDatastoreProvider);
    organizationDao.setOrganizationsPerRequest(RequestLimits.ORGANIZATIONS_PER_REQUEST.getLimit());
    return organizationDao;
  }

  @Bean
  public DatasetService getDatasetService(DatasetDao datasetDao,
      OrganizationDao organizationDao,
      UserWorkflowExecutionDao userWorkflowExecutionDao,
      ScheduledUserWorkflowDao scheduledUserWorkflowDao) {
    return new DatasetService(datasetDao, organizationDao, userWorkflowExecutionDao,
        scheduledUserWorkflowDao);
  }

  @Bean
  public OrganizationService getOrganizationService(OrganizationDao organizationDao,
      DatasetDao datasetDao, ZohoClient zohoClient, MetisSearchService metisSearchService) {
    return new OrganizationService(organizationDao, datasetDao, zohoClient, metisSearchService);
  }

  @Bean
  public MetisAuthorizationService getMetisAuthorizationService() {
    return new MetisAuthorizationService();
  }

  @Bean
  public CrmUserService getCrmUserService(ZohoClient zohoClient) {
    return new CrmUserService(zohoClient);
  }

  @Bean
  public UserWorkflowExecutorManager getUserWorkflowExecutorManager(
      UserWorkflowExecutionDao userWorkflowExecutionDao, Channel rabbitmqChannel,
      RedissonClient redissonClient) {
    UserWorkflowExecutorManager userWorkflowExecutorManager = new UserWorkflowExecutorManager(
        userWorkflowExecutionDao, rabbitmqChannel, redissonClient);
    userWorkflowExecutorManager.setRabbitmqQueueName(rabbitmqQueueName);
    userWorkflowExecutorManager.setMaxConcurrentThreads(maxConcurrentThreads);
    userWorkflowExecutorManager.setMonitorCheckIntervalInSecs(monitorCheckIntervalInSecs);
    return userWorkflowExecutorManager;
  }

  @Bean
  public OrchestratorService getOrchestratorService(UserWorkflowDao userWorkflowDao,
      UserWorkflowExecutionDao userWorkflowExecutionDao,
      ScheduledUserWorkflowDao scheduledUserWorkflowDao,
      DatasetDao datasetDao,
      UserWorkflowExecutorManager userWorkflowExecutorManager) {
    return new OrchestratorService(userWorkflowDao, userWorkflowExecutionDao,
        scheduledUserWorkflowDao, datasetDao, userWorkflowExecutorManager);
  }

  @Override
  public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
    converters.add(new MappingJackson2HttpMessageConverter());
    converters.add(new MappingJackson2XmlHttpMessageConverter());
    super.configureMessageConverters(converters);
  }

  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
    registry.addRedirectViewController("/", "swagger-ui.html");
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry.addResourceHandler("swagger-ui.html")
        .addResourceLocations("classpath:/META-INF/resources/");
    registry.addResourceHandler("/webjars/**")
        .addResourceLocations("classpath:/META-INF/resources/webjars/");
  }

  @Bean
  public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
    return new PropertySourcesPlaceholderConfigurer();
  }

  @Bean
  public Docket api() {
    return new Docket(DocumentationType.SWAGGER_2)
        .select()
        .apis(RequestHandlerSelectors.any())
        .paths(PathSelectors.regex("/.*"))
        .build()
        .directModelSubstitute(ObjectId.class, String.class)
        .useDefaultResponseMessages(false)
        .apiInfo(apiInfo());
  }

  @PreDestroy
  public void close() throws IOException, TimeoutException {
    if (mongoProvider != null) {
      mongoProvider.close();
    }
    if (channel != null && channel.isOpen()) {
      channel.close();
    }
    if (connection != null && connection.isOpen()) {
      connection.close();
    }
  }

  private ApiInfo apiInfo() {
    return new ApiInfo(
        "Metis framework REST API",
        "Metis framework REST API for Europeana",
        "v1",
        "API TOS",
        new Contact("development", "europeana.eu", "development@europeana.eu"),
        "EUPL Licence v1.1",
        ""
    );
  }
}
