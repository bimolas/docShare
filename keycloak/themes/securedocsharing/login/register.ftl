<#import "template.ftl" as layout>
<@layout.registrationLayout bodyClass="register" displayMessage=messagesPerField.existsError('global'); section>
  <#if section = "header">
    <header class="kc-card-header">
      <h1 class="kc-card-title">Create your account</h1>
      <p class="kc-card-subtitle">Start sharing securely in minutes. No credit card required.</p>
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
          <div class="kc-divider"><span>or sign up with email</span></div>
        </#if>
      </#list>
    </#if>

    <form id="kc-register-form" class="kc-form" action="${url.registrationAction}" method="post" novalidate>
      <div class="kc-field-row">
        <div class="kc-field">
          <label class="kc-label" for="firstName">First name</label>
          <input id="firstName" name="firstName"
            class="kc-input<#if messagesPerField.existsError('firstName')> kc-error</#if>"
            type="text" value="${(register.formData.firstName!'')}"
            autocomplete="given-name" placeholder="Jane" required>
          <#if messagesPerField.existsError('firstName')>
            <span class="kc-field-error">${kcSanitize(messagesPerField.get('firstName'))?no_esc}</span>
          </#if>
        </div>
        <div class="kc-field">
          <label class="kc-label" for="lastName">Last name</label>
          <input id="lastName" name="lastName"
            class="kc-input<#if messagesPerField.existsError('lastName')> kc-error</#if>"
            type="text" value="${(register.formData.lastName!'')}"
            autocomplete="family-name" placeholder="Smith" required>
          <#if messagesPerField.existsError('lastName')>
            <span class="kc-field-error">${kcSanitize(messagesPerField.get('lastName'))?no_esc}</span>
          </#if>
        </div>
      </div>

      <div class="kc-field">
        <label class="kc-label" for="email">Work email</label>
        <div class="kc-input-wrap">
          <svg style="position:absolute;left:0.75rem;top:50%;transform:translateY(-50%);width:15px;height:15px;fill:none;stroke:#9ca3af;stroke-width:2;stroke-linecap:round;stroke-linejoin:round;pointer-events:none;" viewBox="0 0 24 24" aria-hidden="true"><rect width="20" height="16" x="2" y="4" rx="2"/><path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/></svg>
          <input id="email" name="email"
            class="kc-input<#if messagesPerField.existsError('email')> kc-error</#if>"
            type="email" value="${(register.formData.email!'')}"
            autocomplete="email" autocapitalize="none"
            placeholder="jane@company.com"
            style="padding-left:2.375rem;"
            required>
        </div>
        <#if messagesPerField.existsError('email')>
          <span class="kc-field-error">${kcSanitize(messagesPerField.get('email'))?no_esc}</span>
        </#if>
      </div>

      <#if !realm.registrationEmailAsUsername>
        <div class="kc-field">
          <label class="kc-label" for="username">Username</label>
          <input id="username" name="username"
            class="kc-input<#if messagesPerField.existsError('username')> kc-error</#if>"
            type="text" value="${(register.formData.username!'')}"
            autocomplete="username" required>
          <#if messagesPerField.existsError('username')>
            <span class="kc-field-error">${kcSanitize(messagesPerField.get('username'))?no_esc}</span>
          </#if>
        </div>
      </#if>

      <#if passwordRequired??>
        <div class="kc-field">
          <label class="kc-label" for="password">Password</label>
          <div class="kc-input-wrap">
            <svg style="position:absolute;left:0.75rem;top:50%;transform:translateY(-50%);width:15px;height:15px;fill:none;stroke:#9ca3af;stroke-width:2;stroke-linecap:round;stroke-linejoin:round;pointer-events:none;" viewBox="0 0 24 24" aria-hidden="true"><rect width="18" height="11" x="3" y="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
            <input id="password" name="password"
              class="kc-input<#if messagesPerField.existsError('password','password-confirm')> kc-error</#if>"
              type="password" autocomplete="new-password"
              placeholder="Create a strong password"
              style="padding-left:2.375rem;"
              required>
            <button type="button" class="kc-input-eye" data-target="password" aria-label="Toggle password visibility">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
            </button>
          </div>

          <div id="kc-password-feedback" class="kc-password-feedback" aria-live="polite" aria-hidden="true">
            <div class="kc-strength-wrap">
              <div class="kc-strength-track"><div id="kc-strength-fill" class="kc-strength-fill" style="width:0%"></div></div>
              <span id="kc-strength-label" class="kc-strength-label"></span>
            </div>
            <div class="kc-hints" aria-label="Password requirements">
              <div class="kc-hint" id="hint-len"><span class="kc-hint-dot"></span><span>8+ characters</span></div>
              <div class="kc-hint" id="hint-upper"><span class="kc-hint-dot"></span><span>Uppercase letter</span></div>
              <div class="kc-hint" id="hint-lower"><span class="kc-hint-dot"></span><span>Lowercase letter</span></div>
              <div class="kc-hint" id="hint-num"><span class="kc-hint-dot"></span><span>Number</span></div>
              <div class="kc-hint" id="hint-special"><span class="kc-hint-dot"></span><span>Special character</span></div>
            </div>
          </div>

          <#if messagesPerField.existsError('password')>
            <span class="kc-field-error">${kcSanitize(messagesPerField.get('password'))?no_esc}</span>
          </#if>
        </div>

        <div class="kc-field">
          <label class="kc-label" for="password-confirm">Confirm password</label>
          <div class="kc-input-wrap">
            <svg style="position:absolute;left:0.75rem;top:50%;transform:translateY(-50%);width:15px;height:15px;fill:none;stroke:#9ca3af;stroke-width:2;stroke-linecap:round;stroke-linejoin:round;pointer-events:none;" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
            <input id="password-confirm" name="password-confirm"
              class="kc-input<#if messagesPerField.existsError('password-confirm')> kc-error</#if>"
              type="password" autocomplete="new-password"
              placeholder="Repeat your password"
              style="padding-left:2.375rem;"
              required>
            <button type="button" class="kc-input-eye" data-target="password-confirm" aria-label="Toggle confirm password visibility">
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
            </button>
          </div>
          <span id="kc-confirm-error" class="kc-field-error kc-client-error" hidden></span>
          <#if messagesPerField.existsError('password-confirm')>
            <span class="kc-field-error">${kcSanitize(messagesPerField.get('password-confirm'))?no_esc}</span>
          </#if>
        </div>
      </#if>

      <label class="kc-checkbox-row">
        <input class="kc-checkbox" type="checkbox" id="kc-terms" name="termsAccepted">
        <span>I agree to the <a href="#" class="kc-link">Terms of Service</a> and <a href="#" class="kc-link">Privacy Policy</a></span>
      </label>

      <button id="kc-register" class="kc-btn-primary" type="submit">
        <span>Create account</span>
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14"/><path d="m12 5 7 7-7 7"/></svg>
      </button>
    </form>
  <#elseif section = "footer">
    <div class="kc-card-footer">
      <p class="kc-footer-text">Already have an account? <a id="link-login" href="${url.loginUrl}" class="kc-link">Sign in</a></p>
    </div>
  </#if>
</@layout.registrationLayout>
