<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false displayWide=false headerNode="">
<!DOCTYPE html>
<html lang="${(locale.currentLanguageTag)!'en'}">
<head>
  <meta charset="utf-8">
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="robots" content="noindex, nofollow">
  <title>${msg("loginTitle", (realm.displayName!''))}</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <#if properties.styles?has_content>
    <#list properties.styles?split(' ') as style>
      <link rel="stylesheet" href="${url.resourcesPath}/${style}">
    </#list>
  </#if>
  <#if properties.stylesCommon?has_content>
    <#list properties.stylesCommon?split(' ') as style>
      <link rel="stylesheet" href="${url.resourcesCommonPath}/${style}">
    </#list>
  </#if>
</head>
<body class="${bodyClass}">
  <div class="kc-bg" aria-hidden="true">
    <div class="kc-bg-orb kc-bg-orb--1"></div>
    <div class="kc-bg-orb kc-bg-orb--2"></div>
    <div class="kc-bg-grid"></div>
  </div>

  <div class="kc-page">
    <main class="kc-main">
      <div class="kc-card">

        <div class="kc-mobile-logo" aria-label="SecureDocSharing">
          <div class="kc-mobile-logo-mark">
            <svg width="16" height="16" viewBox="0 0 32 32" fill="none" aria-hidden="true">
              <path d="M8 13a4 4 0 0 1 4-4h8a4 4 0 0 1 4 4v2a4 4 0 0 1-4 4h-8a4 4 0 0 1-4-4v-2Z" fill="white" opacity="0.95"/>
              <rect x="8" y="21" width="16" height="3.5" rx="1.75" fill="white" opacity="0.55"/>
            </svg>
          </div>
          <span>SecureDocSharing</span>
        </div>

        <#if displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
          <div class="kc-alert kc-alert--${message.type!'info'}" role="alert" aria-live="assertive">
            <#if message.type = 'error'>
              <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
            <#elseif message.type = 'success'>
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
            <#elseif message.type = 'warning'>
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
            <#else>
              <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
            </#if>
            <span>${kcSanitize(message.summary)?no_esc}</span>
          </div>
        </#if>

        <#nested "header">
        <#nested "form">
        <#nested "footer">
      </div>

      <p class="kc-legal">
        By continuing, you agree to SecureDocSharing's
        <a href="#" class="kc-link-subtle">Terms of Service</a> and
        <a href="#" class="kc-link-subtle">Privacy Policy</a>.
      </p>
    </main>
  </div>

  <#if scripts?has_content>
    <#list scripts?split(' ') as script>
      <script src="${url.resourcesPath}/${script}" type="text/javascript"></script>
    </#list>
  </#if>
</body>
</html>
</#macro>
