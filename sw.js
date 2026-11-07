/* PaseoPet · service worker: notificaciones, recordatorio diario y futuros avisos push.
   No guarda la app en caché: siempre carga la versión más reciente desde internet. */
self.addEventListener('install',e=>self.skipWaiting());
self.addEventListener('activate',e=>e.waitUntil(self.clients.claim()));
const ICON='icon-192.png';
function idb(){return new Promise((res,rej)=>{const r=indexedDB.open('paseopet-sw',1);r.onupgradeneeded=()=>r.result.createObjectStore('kv');r.onsuccess=()=>res(r.result);r.onerror=()=>rej(r.error)})}
async function kvGet(k){const db=await idb();return new Promise(res=>{const q=db.transaction('kv').objectStore('kv').get(k);q.onsuccess=()=>res(q.result);q.onerror=()=>res(null)})}
async function kvSet(k,v){const db=await idb();return new Promise(res=>{const t=db.transaction('kv','readwrite');t.objectStore('kv').put(v,k);t.oncomplete=()=>res(true);t.onerror=()=>res(false)})}
const dkey=d=>{d=d||new Date();return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0')};
async function dailyCheck(){const c=await kvGet('cfg');if(!c||!c.on)return;const now=new Date(),k=dkey(now);if(c.walked===k||c.shown===k)return;
  const [h,m]=(c.t||'19:00').split(':').map(Number);if(now.getHours()*60+now.getMinutes()<h*60+m)return;
  c.shown=k;await kvSet('cfg',c);
  const st=c.streak>0?` No pierdas tu racha de ${c.streak} día${c.streak>1?'s':''}.`:'';
  return self.registration.showNotification('Hora de pasear 🐾',{body:`${c.name||'Tu perro'} está esperando su paseo de hoy.${st}`,icon:ICON,badge:ICON,tag:'diario',data:{tab:'walk'}})}
self.addEventListener('periodicsync',e=>{if(e.tag==='paseo-diario')e.waitUntil(dailyCheck())});
self.addEventListener('push',e=>{let d={};try{d=e.data?e.data.json():{}}catch(x){d={body:e.data&&e.data.text()}}
  e.waitUntil(self.registration.showNotification(d.title||'PaseoPet',{body:d.body||'',icon:ICON,badge:ICON,tag:d.tag||'push',data:{tab:d.tab||'home'}}))});
self.addEventListener('notificationclick',e=>{e.notification.close();const tab=(e.notification.data&&e.notification.data.tab)||'home';
  e.waitUntil((async()=>{const all=await self.clients.matchAll({type:'window',includeUncontrolled:true});for(const c of all){try{await c.focus();c.postMessage({pp:'tab',tab});return}catch(x){}}return self.clients.openWindow('./?tab='+tab)})())});
self.addEventListener('message',e=>{if(e.data&&e.data.pp==='cfg')e.waitUntil(kvSet('cfg',e.data.cfg))});
