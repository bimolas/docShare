<#import "template.ftl" as layout>
<@layout.registrationLayout bodyClass="verify-email" displayMessage=false; section>
  <#if section = "header">
    <header class="kc-card-header kc-centered-header">
      <div class="kc-verify-icon" aria-hidden="true">
        <svg viewBox="0 0 24 24"><rect x="2" y="4" width="20" height="16" rx="2"/><path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/></svg>
      </div>
      <h1 class="kc-card-title">Check your inbox</h1>
      <p class="kc-card-subtitle">We've sent a verification link to your email. Click it to activate your account.</p>
    </header>
  <#elseif section = "form">
    <div class="kc-verify-steps" role="list" aria-label="Verification steps">
      <div class="kc-verify-step" role="listitem">
        <span class="kc-verify-step-num" aria-hidden="true">1</span>
        <span>Open the email from <strong>SecureDocSharing</strong></span>
      </div>
      <div class="kc-verify-step" role="listitem">
        <span class="kc-verify-step-num" aria-hidden="true">2</span>
        <span>Click <strong>"Verify email address"</strong></span>
      </div>
      <div class="kc-verify-step" role="listitem">
        <span class="kc-verify-step-num" aria-hidden="true">3</span>
        <span>Return here to continue</span>
      </div>
    </div>

    <#if message?has_content>
      <div class="kc-info-box" role="status">${kcSanitize(message.summary)?no_esc}</div>
    </#if>

    <div class="kc-reset-actions">
      <a href="${url.loginUrl}" class="kc-btn-primary">
        <svg viewBox="0 0 24 24" aria-hidden="true" style="width:15px;height:15px;fill:none;stroke:currentColor;stroke-width:2.5;stroke-linecap:round;stroke-linejoin:round;"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
        <span>I've verified — Continue</span>
      </a>
      <a id="link-verify-resend" href="${url.loginAction}" class="kc-btn-ghost">
        <svg viewBox="0 0 24 24" aria-hidden="true" style="width:14px;height:14px;fill:none;stroke:currentColor;stroke-width:2;stroke-linecap:round;stroke-linejoin:round;"><path d="M4 4v5h.582m15.356 2A8.001 8.001 0 0 0 4.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 0 1-15.357-2m15.357 2H15"/></svg>
        Resend verification email
      </a>
    </div>
  <#elseif section = "footer"></#if>
</@layout.registrationLayout>
