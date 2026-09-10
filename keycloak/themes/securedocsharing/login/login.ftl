<#import "template.ftl" as layout>
<@layout.registrationLayout bodyClass="login" displayMessage=!messagesPerField.existsError('username','password'); section>
  <#if section = "header">
    <header class="kc-card-header">
      <h1 class="kc-card-title">Welcome back</h1>
      <p class="kc-card-subtitle">Sign in to your SecureDocSharing account</p>
    </header>
  <#elseif section = "form">

    <#if social.providers??>
      <#list social.providers as p>
        <#if p.alias = "google">
          <a id="social-${p.alias}" class="kc-btn-social" href="${p.loginUrl}">
            <svg width="18" height="18" viewBox="0 0 18 18" aria-hidden="true">
              <path d="M17.64 9.2a10.34 10.34 0 0 0-.16-1.84H9v3.48h4.84a4.14 4.14 0 0 1-1.8 2.72v2.26h2.92a8.78 8.78 0 0 0 2.68-6.62Z" fill="#4285F4"/>
              <path d="M9 18a8.59 8.59 0 0 0 5.96-2.18l-2.91-2.26A5.43 5.43 0 0 1 9 14.7a5.41 5.41 0 0 1-5.08-3.74H.96A9 9 0 0 0 9 18Z" fill="#34A853"/>
              <path d="M3.92 10.96A5.44 5.44 0 0 1 3.63 9a5.44 5.44 0 0 1 .29-.96V5.71H.96A9 9 0 0 0 0 9a9 9 0 0 0 .96 3.29l2.96-2.33Z" fill="#FBBC05"/>
              <path d="M9 3.58a4.86 4.86 0 0 1 3.44 1.35l2.58-2.58A8.64 8.64 0 0 0 9 0 9 9 0 0 0 .96 5.71L3.92 8.04A5.41 5.41 0 0 1 9 3.58Z" fill="#EA4335"/>
            </svg>
            <span>Continue with Google</span>
          </a>
          <div class="kc-divider"><span>or continue with email</span></div>
        </#if>
      </#list>
    </#if>

    <form id="kc-form-login" class="kc-form" action="${url.loginAction}" method="post">

      <div class="kc-field">
        <label class="kc-label" for="username">Email address</label>
        <div class="kc-input-wrap">
          <svg class="kc-input-icon" viewBox="0 0 24 24" aria-hidden="true" style="position:absolute;left:0.75rem;top:50%;transform:translateY(-50%);width:15px;height:15px;flex:0 0 15px;fill:none;stroke:#9ca3af;stroke-width:2;stroke-linecap:round;stroke-linejoin:round;pointer-events:none;"><rect width="20" height="16" x="2" y="4" rx="2"/><path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/></svg>
          <input id="username" name="username"
            class="kc-input<#if messagesPerField.existsError('username','password')> kc-error</#if>"
            type="text" value="${(login.username!'')}" autocomplete="username" autofocus
            placeholder="you@company.com"
            style="padding-left:2.375rem;"
            aria-invalid="${(messagesPerField.existsError('username','password'))?string('true','false')}">
        </div>
        <#if messagesPerField.existsError('username')>
          <span class="kc-field-error">
            <svg viewBox="0 0 24 24" style="width:12px;height:12px;flex:0 0 12px;fill:none;stroke:currentColor;stroke-width:2;stroke-linecap:round;stroke-linejoin:round;" aria-hidden="true"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
            ${kcSanitize(messagesPerField.get('username'))?no_esc}
          </span>
        </#if>
      </div>

      <div class="kc-field">
        <label class="kc-label" for="password">
          <span>Password</span>
          <#if realm.resetPasswordAllowed>
            <a href="${url.loginResetCredentialsUrl}">Forgot password?</a>
          </#if>
        </label>
        <div class="kc-input-wrap">
          <svg class="kc-input-icon" viewBox="0 0 24 24" aria-hidden="true" style="position:absolute;left:0.75rem;top:50%;transform:translateY(-50%);width:15px;height:15px;fill:none;stroke:#9ca3af;stroke-width:2;stroke-linecap:round;stroke-linejoin:round;pointer-events:none;"><rect width="18" height="11" x="3" y="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
          <input id="password" name="password"
            class="kc-input<#if messagesPerField.existsError('password')> kc-error</#if>"
            type="password" autocomplete="current-password" placeholder="Enter your password"
            style="padding-left:2.375rem;"
            aria-invalid="${(messagesPerField.existsError('password'))?string('true','false')}">
          <button type="button" class="kc-input-eye" data-target="password" aria-label="Toggle password visibility" aria-pressed="false">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
          </button>
        </div>
        <#if messagesPerField.existsError('password')>
          <span class="kc-field-error">
            <svg viewBox="0 0 24 24" style="width:12px;height:12px;flex:0 0 12px;fill:none;stroke:currentColor;stroke-width:2;stroke-linecap:round;stroke-linejoin:round;" aria-hidden="true"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
            ${kcSanitize(messagesPerField.get('password'))?no_esc}
          </span>
        </#if>
      </div>

      <#if realm.rememberMe && !usernameEditDisabled??>
        <label class="kc-checkbox-row kc-remember-row">
          <input class="kc-checkbox" type="checkbox" id="rememberMe" name="rememberMe" <#if login.rememberMe?? && login.rememberMe>checked</#if>>
          <span>Keep me signed in</span>
        </label>
      </#if>

      <button id="kc-login" class="kc-btn-primary" type="submit">
        <span>Sign in</span>
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14"/><path d="m12 5 7 7-7 7"/></svg>
      </button>
    </form>
  <#elseif section = "footer">
    <div class="kc-card-footer">
      <p class="kc-footer-text">Don't have an account? <a href="${url.registrationUrl}" class="kc-link">Create one for free</a></p>
    </div>
  </#if>
</@layout.registrationLayout>
