package eu.europeana.metis.common;

import eu.europeana.metis.controller.MetisUserPageController;
import java.io.IOException;
import java.util.Collection;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.util.CollectionUtils;

/**
 * @author Simon Tzanakis (Simon.Tzanakis@europeana.eu)
 * @since 2017-06-19
 */
public class SimpleUrlAuthenticationSuccessHandler
    implements AuthenticationSuccessHandler {
  private final Logger LOGGER = LoggerFactory.getLogger(MetisUserPageController.class);

  private RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

  @Override
  public void onAuthenticationSuccess(HttpServletRequest request,
      HttpServletResponse response, Authentication authentication)
      throws IOException {

    handle(request, response, authentication);
    clearAuthenticationAttributes(request);
  }

  protected void handle(HttpServletRequest request,
      HttpServletResponse response, Authentication authentication)
      throws IOException {

    String targetUrl = determineTargetUrl(authentication);

    if (response.isCommitted()) {
      LOGGER.debug(String.format("Response has already been committed. Unable to redirect to %s", targetUrl));
      return;
    }

    redirectStrategy.sendRedirect(request, response, targetUrl);
  }

  protected String determineTargetUrl(Authentication authentication) {
    boolean isNonApprovedUser = false;
    Collection<? extends GrantedAuthority> authorities
        = authentication.getAuthorities();
    if (CollectionUtils.isEmpty(authorities))
      isNonApprovedUser = true;

    if (isNonApprovedUser) {
      return "/profile";
    } else{
      return "/dashboard";
    }
  }

  protected void clearAuthenticationAttributes(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session == null) {
      return;
    }
    session.removeAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
  }

  public void setRedirectStrategy(RedirectStrategy redirectStrategy) {
    this.redirectStrategy = redirectStrategy;
  }
  protected RedirectStrategy getRedirectStrategy() {
    return redirectStrategy;
  }
}
