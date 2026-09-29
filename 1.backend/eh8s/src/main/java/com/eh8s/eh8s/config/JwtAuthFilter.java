package com.eh8s.eh8s.config;

import com.eh8s.eh8s.service.interfaces.IJwtService;
import com.eh8s.eh8s.service.interfaces.JwtPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads {@code Authorization: Bearer} and sets the security context via {@link IJwtService}.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

  private final IJwtService jwtService;

  /**
   * Creates the filter.
   *
   * @param jwtService token parser
   */
  public JwtAuthFilter(IJwtService jwtService) {
    this.jwtService = jwtService;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
      String compact = header.substring(7).trim();
      jwtService
          .parse(compact)
          .ifPresent(
              principal ->
                  SecurityContextHolder.getContext()
                      .setAuthentication(authentication(principal)));
    }
    filterChain.doFilter(request, response);
  }

  private static UsernamePasswordAuthenticationToken authentication(JwtPrincipal principal) {
    return new UsernamePasswordAuthenticationToken(principal, null, List.of());
  }
}
