package eu.europeana.metis.controller;

import eu.europeana.metis.page.MetisLandingPage;
import eu.europeana.metis.page.MetisPageFactory;
import eu.europeana.metis.ui.mongo.domain.UserDTO;
import eu.europeana.metis.ui.mongo.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.ldap.userdetails.LdapUserDetailsImpl;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

/**
 * @author Simon Tzanakis (Simon.Tzanakis@europeana.eu)
 * @since 2017-06-20
 */
@Controller
public class MetisProfilePageController {
  private static final Logger LOGGER = LoggerFactory.getLogger(MetisUserPageController.class);

  private final UserService userService;
  private final MetisPageFactory pageFactory;

  @Autowired
  public MetisProfilePageController(UserService userService, MetisPageFactory pageFactory) {
    this.userService = userService;
    this.pageFactory =  pageFactory;
  }

  @RequestMapping(value = "/profile", method = RequestMethod.GET)
  public ModelAndView profile(Model model) {
    UserDTO userDTO = getAuthenticatedUser();

    MetisLandingPage metisLandingPage = pageFactory.createProfileLandingPage(userDTO);

    ModelAndView modelAndView = new ModelAndView("templates/Pandora/Metis-Homepage");
    modelAndView.addAllObjects(metisLandingPage.buildModel());
    return modelAndView;
  }

  private UserDTO getAuthenticatedUser() {
    Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    String primaryKey =
        principal instanceof LdapUserDetailsImpl ? ((LdapUserDetailsImpl) principal).getUsername()
            : null;
    UserDTO userDTO = userService.getUser(primaryKey);
    LOGGER.info("User profile opened: %s", userDTO.getLdapUser().getFirstName());
    return userDTO;
  }

//  private List<Organization> buildAvailableOrganizationsList() {
////    List<String> organizations = new ArrayList<>();
//    try {
//      List<OrganizationRole> roles = Arrays
//          .asList(OrganizationRole.DATA_AGGREGATOR, OrganizationRole.CONTENT_PROVIDER,
//              OrganizationRole.DIRECT_PROVIDER,
//              OrganizationRole.EUROPEANA);
//      List<Organization> organizationsByRoles = dsOrgRestClient.getAllOrganizationsByRoles(roles);
////      if (organizationsByRoles != null && !organizationsByRoles.isEmpty()) {
////        for (Organization o : organizationsByRoles) {
////          organizations.add(o.getName());
////        }
////      }
//      return organizationsByRoles;
//    } catch (ServerException e) {
//      LOGGER.error("ERROR: *** Zoho server exception: %s", e.getMessage());
//    } catch (Exception e) {
//      LOGGER.error("ERROR: *** CMS exception: ", e.getMessage());
//    }
//    return null;
//  }

}