package eu.europeana.metis.config;

import com.mongodb.MongoClientOptions;
import com.mongodb.MongoClientURI;
import eu.europeana.corelib.storage.impl.MongoProviderImpl;
import eu.europeana.metis.core.mongo.MorphiaDatastoreProvider;
import eu.europeana.metis.service.ExampleMappingService;
import eu.europeana.metis.service.MappingService;
import eu.europeana.metis.ui.ldap.dao.UserDao;
import eu.europeana.metis.ui.ldap.dao.impl.LdapUserDao;
import eu.europeana.metis.ui.mongo.dao.MongoUserDao;
import eu.europeana.metis.ui.mongo.dao.RoleRequestDao;
import eu.europeana.metis.ui.mongo.domain.User;
import eu.europeana.metis.ui.mongo.domain.RoleRequest;
import eu.europeana.metis.ui.mongo.service.UserService;
import eu.europeana.metis.utils.PivotalCloudFoundryServicesReader;
import javax.annotation.PreDestroy;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

/**
 * The configuration is for DB access (MongoDB), where the user account data such as Skype account,
 * Country, Organization, etc. is stored.
 *
 * @author alena
 */
@Configuration
@PropertySource("classpath:mongo.properties")
public class MetisConfig implements InitializingBean {
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

  private MongoProviderImpl mongoProvider;
  private MorphiaDatastoreProvider moprhiaDatastoreProvider;

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
      if (mongoClientURI != null) {
        String mongoHostAndPort = mongoClientURI.getHosts().get(0);
        mongoHosts = mongoHostAndPort.substring(0, mongoHostAndPort.lastIndexOf(":"));
        mongoPort = Integer
            .parseInt(mongoHostAndPort.substring(mongoHostAndPort.lastIndexOf(":") + 1));
        mongoUsername = mongoClientURI.getUsername();
        mongoPassword = String.valueOf(mongoClientURI.getPassword());
        mongoDb = mongoClientURI.getDatabase();
      }
    }

    String[] mongoHostsArray = mongoHosts.split(",");
    StringBuilder mongoPorts = new StringBuilder();
    for (int i = 0; i < mongoHostsArray.length; i++) {
      mongoPorts.append(mongoPort + ",");
    }
    mongoPorts.replace(mongoPorts.lastIndexOf(","), mongoPorts.lastIndexOf(","), "");
    MongoClientOptions.Builder options = MongoClientOptions.builder();
    options.socketKeepAlive(true);
    mongoProvider = new MongoProviderImpl(mongoHosts, mongoPorts.toString(), mongoDb, mongoUsername,
        mongoPassword, options);
  }

  @Bean
  public UserDao userDao() {
    return new LdapUserDao();
  }

  @Bean
  @DependsOn(value = "morphiaDatastoreProvider")
  public MongoUserDao dbUserDao() {
    return new MongoUserDao(User.class, moprhiaDatastoreProvider.getDatastore());
  }

  @Bean
  @DependsOn(value = "morphiaDatastoreProvider")
  public RoleRequestDao roleRequestDao() {
    return new RoleRequestDao(RoleRequest.class, moprhiaDatastoreProvider.getDatastore());
  }

  @Bean(name = "morphiaDatastoreProvider")
  MorphiaDatastoreProvider getMongoProvider() {
      moprhiaDatastoreProvider = new MorphiaDatastoreProvider(mongoProvider.getMongo(), mongoDb);
      return moprhiaDatastoreProvider;
  }

  @Bean
  public UserService userService(UserDao userDao, MongoUserDao mongoUserDao, RoleRequestDao roleRequestDao) {
    return new UserService(userDao, mongoUserDao, roleRequestDao);
  }

  @Bean
  public MappingService mappingService() {
    return new ExampleMappingService();
  }

  @Bean
  public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
    return new PropertySourcesPlaceholderConfigurer();
  }

  @PreDestroy
  public void close()
  {
    if (mongoProvider != null)
      mongoProvider.close();
  }

}
