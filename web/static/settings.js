const theme=document.getElementById('siteTheme');theme.value=localStorage.getItem('yomu.theme')||'system';theme.onchange=()=>{localStorage.setItem('yomu.theme',theme.value);applyYomuTheme();};
