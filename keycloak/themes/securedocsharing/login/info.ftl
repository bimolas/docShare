<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
  <#if section = "header">
    <header class="kc-card-header">
      <div class="kc-verify-icon" aria-hidden="true" style="background:#f0fdf4;border-color:#bbf7d0;color:#16a34a;">
        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>
      </div>
      <h2 class="kc-card-title">${message.summary!''}</h2>
    </header>
  <#elseif section = "form">
    <#if requiredActions??>
      <div class="kc-info-box">
        <#list requiredActions as action>
          <strong>${msg("requiredAction.${action}")}</strong><br>
        </#list>
      </div>
    </#if>
    <#if pageRedirectUri?has_content>
      <a href="${pageRedirectUri}" class="kc-btn-primary" style="text-align:center;">Continue</a>
    <#elseif actionUri?has_content>
      <a href="${actionUri}" class="kc-btn-primary" style="text-align:center;">${msg("proceedWithAction")}</a>
    <#elseif client.baseUrl?has_content>
      <a href="${client.baseUrl}" class="kc-btn-primary" style="text-align:center;">← Back to application</a>
    </#if>
  <#elseif section = "footer"></#if>
</@layout.registrationLayout>