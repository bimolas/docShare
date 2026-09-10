<#import "template.ftl" as layout>
<@layout.registrationLayout bodyClass="reset-password" displayInfo=true displayMessage=!messagesPerField.existsError('username'); section>
  <#if section = "header">
    <header class="kc-card-header">
      <h1 class="kc-card-title">Reset your password</h1>
      <p class="kc-card-subtitle">Enter your email and we'll send you a secure reset link.</p>
    </header>
  <#elseif section = "form">
    <form id="kc-reset-password-form" class="kc-form" action="${url.loginAction}" method="post">
      <div class="kc-field">
        <label class="kc-label" for="username">Email address</label>
        <div class="kc-input-wrap">
          <svg style="position:absolute;left:0.75rem;top:50%;transform:translateY(-50%);width:15px;height:15px;fill:none;stroke:#9ca3af;stroke-width:2;stroke-linecap:round;stroke-linejoin:round;pointer-events:none;" viewBox="0 0 24 24" aria-hidden="true"><rect width="20" height="16" x="2" y="4" rx="2"/><path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/></svg>
          <input
            id="username" name="username"
            class="kc-input<#if messagesPerField.existsError('username')> kc-error</#if>"
            type="email"
            value="${(auth.attemptedUsername!'')}"
            autocomplete="email"
            autofocus
            placeholder="you@company.com"
            style="padding-left:2.375rem;"
          >
        </div>
        <#if messagesPerField.existsError('username')>
          <span class="kc-field-error">
            <svg viewBox="0 0 24 24" style="width:12px;height:12px;flex:0 0 12px;fill:none;stroke:currentColor;stroke-width:2;stroke-linecap:round;stroke-linejoin:round;" aria-hidden="true"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
            ${kcSanitize(messagesPerField.get('username'))?no_esc}
          </span>
        </#if>
      </div>

      <button id="kc-reset" class="kc-btn-primary" type="submit">
        <svg viewBox="0 0 24 24" aria-hidden="true" style="width:15px;height:15px;fill:none;stroke:currentColor;stroke-width:2;stroke-linecap:round;stroke-linejoin:round;"><path d="M4 4v5h.582m15.356 2A8.001 8.001 0 0 0 4.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 0 1-15.357-2m15.357 2H15"/></svg>
        <span>Send reset link</span>
      </button>

      <a href="${url.loginUrl}" class="kc-btn-ghost">
        <svg viewBox="0 0 24 24" aria-hidden="true" style="width:14px;height:14px;fill:none;stroke:currentColor;stroke-width:2;stroke-linecap:round;stroke-linejoin:round;"><path d="m15 18-6-6 6-6"/></svg>
        Back to sign in
      </a>
    </form>
  <#elseif section = "footer"></#if>
</@layout.registrationLayout>