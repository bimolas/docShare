<#import "template.ftl" as layout>
<@layout.registrationLayout bodyClass="update-password" displayMessage=!messagesPerField.existsError('password','password-confirm'); section>
  <#if section = "header">
    <header class="kc-card-header">
      <h1 class="kc-card-title">Set new password</h1>
      <p class="kc-card-subtitle">Choose a strong password to protect your account.</p>
    </header>
  <#elseif section = "form">
    <form id="kc-passwd-update-form" class="kc-form" action="${url.loginAction}" method="post">
      <#if isAppInitiatedAction??>
        <input type="hidden" id="logout-sessions" name="logout-sessions" value="on">
      </#if>

      <div class="kc-field">
        <label class="kc-label" for="password-new">New password</label>
        <div class="kc-input-wrap">
          <svg style="position:absolute;left:0.75rem;top:50%;transform:translateY(-50%);width:15px;height:15px;fill:none;stroke:#9ca3af;stroke-width:2;stroke-linecap:round;stroke-linejoin:round;pointer-events:none;" viewBox="0 0 24 24" aria-hidden="true"><rect width="18" height="11" x="3" y="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
          <input
            id="password-new" name="password-new"
            class="kc-input<#if messagesPerField.existsError('password','password-confirm')> kc-error</#if>"
            type="password" autocomplete="new-password" autofocus
            placeholder="Create a strong password"
            style="padding-left:2.375rem;"
          >
          <button type="button" class="kc-input-eye" aria-label="Toggle new password visibility"
            onclick="(function(){var i=document.getElementById('password-new');i.type=i.type==='password'?'text':'password'})()" >
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
        <label class="kc-label" for="password-confirm">Confirm new password</label>
        <div class="kc-input-wrap">
          <svg style="position:absolute;left:0.75rem;top:50%;transform:translateY(-50%);width:15px;height:15px;fill:none;stroke:#9ca3af;stroke-width:2;stroke-linecap:round;stroke-linejoin:round;pointer-events:none;" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
          <input
            id="password-confirm" name="password-confirm"
            class="kc-input<#if messagesPerField.existsError('password-confirm')> kc-error</#if>"
            type="password" autocomplete="new-password"
            placeholder="Repeat your password"
            style="padding-left:2.375rem;"
          >
          <button type="button" class="kc-input-eye" aria-label="Toggle confirm password visibility"
            onclick="(function(){var i=document.getElementById('password-confirm');i.type=i.type==='password'?'text':'password'})()" >
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
          </button>
        </div>
        <#if messagesPerField.existsError('password-confirm')>
          <span class="kc-field-error">${kcSanitize(messagesPerField.get('password-confirm'))?no_esc}</span>
        </#if>
      </div>

      <button id="kc-update-password" class="kc-btn-primary" type="submit">
        <svg viewBox="0 0 24 24" aria-hidden="true" style="width:15px;height:15px;fill:none;stroke:currentColor;stroke-width:2.5;stroke-linecap:round;stroke-linejoin:round;"><path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"/><path d="m9 12 2 2 4-4"/></svg>
        <span>Update password</span>
      </button>
    </form>
  <#elseif section = "footer"></#if>
</@layout.registrationLayout>