'use strict';
const $ = id => document.getElementById(id);
let auth = '', user = null, source = null, criteria = null, result = null, selected = null;
let busy = false, polling = false, revision = 0, forcedWarning = false, disconnected = false;
let cardsKey = '';
const time = value => new Date(value).toLocaleString('ru-RU', {timeZone:'Europe/Moscow', hour12:false});
const number = value => Number(value).toLocaleString('ru-RU', {maximumFractionDigits:2});
const text = (tag, value, className) => {const el=document.createElement(tag);el.textContent=value;if(className)el.className=className;return el;};
function message(value='', success=false) { $('message').hidden=true;$('notice').hidden=true; if(value){const el=$(success?'notice':'message');el.textContent=value;el.hidden=false;} }
async function request(path, method='GET', body, credentials=auth) {
  let response;
  try {response=await fetch('/api/'+path,{method,headers:{...(credentials?{Authorization:credentials}:{}),...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(5000)});}
  catch {throw new Error('Нет связи с сервером. Проверьте соединение и повторите действие.');}
  const data=await response.json();
  if(!response.ok)throw new Error(data.message || `Ошибка сервера: ${response.status}`);
  return data;
}
function controls() {
  document.querySelectorAll('button, input, select').forEach(button => {button.disabled=busy;});
  $('start').disabled=busy||!source||source.running||source.completed;
  $('stop').disabled=busy||!source||!source.running;
  $('reset').disabled=busy||!source||source.running;
  $('book').disabled=busy||user?.role!=='MEMBER'||!selected;
}
async function act(action) {
  if(busy)return;busy=true;revision++;controls();message();
  try {await action();} catch(error){message(error.message);} finally{busy=false;controls();}
}
function resetSearch() {revision++;criteria=null;result=null;selected=null;forcedWarning=false;cardsKey='';$('rooms').replaceChildren();$('room-details').hidden=true;$('search-summary').textContent='Условия изменены. Нажмите «Найти аудитории».';}
function logout() {auth='';user=null;revision++;resetSearch();$('workspace').hidden=true;$('login-form').hidden=false;$('logout').hidden=true;$('account').textContent='Вы не вошли.';$('documents').replaceChildren();$('bookings').replaceChildren();$('purpose').value='';$('password').value='';message();}
$('logout').onclick=()=>{if(!busy)logout();};
$('login-form').onsubmit=event=>{event.preventDefault();act(async()=>{
  const credentials='Basic '+btoa(unescape(encodeURIComponent($('login').value+':'+$('password').value)));
  const account=await request('booking/me','GET',undefined,credentials);auth=credentials;user=account;
  $('password').value='';$('login-form').hidden=true;$('logout').hidden=false;$('workspace').hidden=false;
  $('account').textContent=`${user.login} | ${user.role} - ${user.role==='MEMBER'?'поиск, создание и отмена своих броней':'только просмотр'}`;
  await loadBookings();message('Вход выполнен.',true);
});};
$('mode').onchange=()=>{$('date-label').hidden=$('mode').value==='now';$('date').required=$('mode').value==='at';};
$('search-form').addEventListener('input',resetSearch);
function searchPath(c) {return c.now?'booking/rooms/now?'+new URLSearchParams({durationMinutes:c.minutes,participants:c.participants}):'booking/rooms?'+new URLSearchParams({start:c.start,end:c.end,participants:c.participants});}
$('search-form').onsubmit=event=>{event.preventDefault();act(async()=>{
  const minutes=Number($('duration').value), participants=Number($('participants').value), now=$('mode').value==='now';
  if(!Number.isInteger(minutes)||minutes<1||minutes>120||!Number.isInteger(participants)||participants<1)throw new Error('Укажите длительность от 1 до 120 минут и целое положительное число участников.');
  const c={now,minutes,participants};
  if(!now){const start=new Date($('date').value+':00+03:00');if(!Number.isFinite(start.getTime()))throw new Error('Укажите начало занятия.');c.start=start.toISOString();c.end=new Date(start.getTime()+minutes*60000).toISOString();}
  const found=await request(searchPath(c));criteria=c;result=found;selected=null;forcedWarning=false;renderSearch();
});};
function renderSearch() {
  if(!result)return;
  $('clock').textContent=`Модельное время: ${time(result.demoTime)} (Москва)`;
  $('search-summary').textContent=`${time(result.start)} - ${time(result.end)} (Москва) | ${criteria.participants} участников. ${result.rooms.length?'Найдено: '+result.rooms.length:'Подходящих аудиторий нет. Измените время или число участников.'}`;
  const key=JSON.stringify([selected,result.rooms.map(o=>[o.room,o.currentUse.state,o.currentUse.label,o.recommended,o.explanation])]);
  if(key!==cardsKey){
  const focusedRoom=document.activeElement?.dataset.room;
  cardsKey=key;$('rooms').replaceChildren();
  for(const offer of result.rooms){const card=text('article','', 'card'+(selected===offer.room.id?' selected':''));card.append(text('h3',`Аудитория ${offer.room.name}`),text('p',`${offer.room.capacity} мест | свободна по броням`));
    card.append(text('p','Сейчас: '+offer.currentUse.label,'tag'+(offer.currentUse.state==='PRESENT'?' warn':offer.recommended?' good':'')));
    card.append(text('p',offer.explanation,'muted'));const button=text('button',selected===offer.room.id?'Выбрана':'Подробнее и выбор','secondary');button.dataset.room=offer.room.id;button.onclick=()=>{if(busy)return;selected=offer.room.id;forcedWarning=false;$('ack').checked=false;renderSearch();$('room-details').scrollIntoView({behavior:'smooth',block:'nearest'});};card.append(button);$('rooms').append(card);
  }
  if(focusedRoom)Array.from($('rooms').querySelectorAll('button')).find(b=>b.dataset.room===focusedRoom)?.focus({preventScroll:true});
  }
  if(selected&&!result.rooms.some(o=>o.room.id===selected)){selected=null;forcedWarning=false;}
  renderSelected();
}
function renderSelected(){
  const offer=result?.rooms.find(o=>o.room.id===selected);$('room-details').hidden=!offer;if(!offer)return;
  $('room-title').textContent=`Аудитория ${offer.room.name} | ${offer.room.capacity} мест`;
  $('room-advice').textContent='Сейчас: '+offer.currentUse.label;
  $('room-explanation').textContent=offer.explanation;
  const canBook=user?.role==='MEMBER';$('booking-form').hidden=!canBook;$('reader-note').hidden=canBook;
  const warning=criteria.now&&(offer.currentUse.state==='PRESENT'||forcedWarning);
  $('ack-label').hidden=!warning;$('ack').required=warning;
  if(!warning)$('ack').checked=false;
  $('booking-note').textContent=criteria.now?'При подтверждении начало и окончание будут заново рассчитаны по модельному времени сервера. Длительность: '+criteria.minutes+' мин.':`Бронь: ${time(result.start)} - ${time(result.end)} (Москва). Текущие измерения не являются прогнозом.`;
  renderMeasurements();controls();
}
$('booking-form').onsubmit=event=>{event.preventDefault();act(async()=>{
  if(!selected||!criteria||user?.role!=='MEMBER')throw new Error('Для бронирования выберите аудиторию и войдите как MEMBER.');
  const purpose=$('purpose').value.trim();if(!purpose)throw new Error('Укажите цель занятия.');
  let created;
  try {created=await request('booking/bookings'+(criteria.now?'/now':''),'POST',criteria.now?{roomId:selected,durationMinutes:criteria.minutes,participants:criteria.participants,purpose,acknowledgePresence:$('ack').checked}:{roomId:selected,start:criteria.start,end:criteria.end,participants:criteria.participants,purpose});}
  catch(error){
    if(criteria.now&&error.message.includes('Подтвердите ознакомление')){
      $('ack').checked=false;
      try {await reloadSearch();}
      catch {resetSearch();throw new Error('Состояние аудитории изменилось, но обновить результаты не удалось. Повторите поиск.');}
      if(!selected)throw new Error('Аудитория больше недоступна. Выберите другую комнату.');
      const current=result.rooms.find(offer=>offer.room.id===selected);
      if(current.currentUse.state!=='PRESENT')throw new Error('Состояние аудитории изменилось. Проверьте обновлённые сведения и подтвердите бронь заново.');
    }
    throw error;
  }
  const booking=created.booking||created;selected=null;$('purpose').value='';$('ack').checked=false;forcedWarning=false;
  await loadBookings();await reloadSearch();message(`Бронь №${booking.id} создана: ${time(booking.start)} - ${time(booking.end)} (Москва).${created.warning?' '+created.warning:''}`,true);
});};
async function loadBookings(){const items=await request('booking/bookings');renderBookings(items);}
function renderBookings(items){$('bookings').replaceChildren();if(!items.length)$('bookings').append(text('p','У вас пока нет броней.','muted'));
  for(const b of items){const row=text('article','','booking'+(b.status==='CANCELLED'?' cancelled':''));row.append(text('h3',`Бронь №${b.id} | аудитория ${100+b.roomId}`),text('p',`${time(b.start)} - ${time(b.end)} (Москва)`),text('p',`${b.participants} участников | ${b.purpose}`),text('p',b.status==='ACTIVE'?'Активна':'Отменена','tag'));
    if(user.role==='MEMBER'&&b.status==='ACTIVE'){const cancel=text('button',`Отменить бронь №${b.id}`,'secondary');cancel.onclick=()=>act(async()=>{await request(`booking/bookings/${b.id}/cancel`,'POST');await loadBookings();await reloadSearch();message(`Бронь №${b.id} отменена.`,true);});row.append(cancel);}$('bookings').append(row);
  }
}
$('refresh-bookings').onclick=()=>act(loadBookings);
$('rules-form').onsubmit=event=>{event.preventDefault();act(async()=>{const query=$('query').value.trim();if(!query)throw new Error('Введите слово или фразу для поиска правил.');const items=await request('booking/documents?'+new URLSearchParams({query}));$('documents').replaceChildren();if(!items.length)$('documents').append(text('p','Доступные правила по этому запросу не найдены.','muted'));for(const d of items){const article=document.createElement('article');article.append(text('h3',d.title),text('p',d.excerpt),text('p',d.source,'muted'));$('documents').append(article);}});};
function renderSource(data){source=data;$('status').textContent=data.running?'Идёт чтение файла':data.completed?'Файл прочитан':'Чтение остановлено';
  const last=data.measurements.at(-1);$('clock').textContent=`Модельное время: ${time(last?.measuredAt||data.startedAt)} (Москва)`;
  controls();renderMeasurements();if(data.error)message(data.error);
}
function renderMeasurements(){if(!source||!selected)return;const same=source.roomId===selected;
  $('source-info').textContent=same?`Источник: ${source.sourceName}. Прочитано ${source.generated} из ${source.totalRecords}.`:'Для этой аудитории источник не подключён. Измерений нет.';
  $('settings-info').textContent=same?`Период ${source.settings.samplePeriodMs/1000} с | округление ${source.settings.quantumPercent}% | окно ${source.settings.averagingWindow} | буфер ${source.settings.bufferCapacity}`:'';
  const scroll=$('measurements').closest('.table-wrap'), scrollTop=scroll.scrollTop, scrollLeft=scroll.scrollLeft;
  $('measurements').replaceChildren();for(const m of same?source.measurements:[]){const tr=document.createElement('tr');for(const v of [m.sequence,time(m.measuredAt),number(m.sourceValue),number(m.rawValue),number(m.filteredValue)])tr.append(text('td',v));$('measurements').append(tr);}
  if(!same||!source.measurements.length){const tr=document.createElement('tr'),td=text('td',same?'Измерений пока нет. Откройте «Источник измерений» и нажмите «Старт».':'Нет измерений.');td.colSpan=5;tr.append(td);$('measurements').append(tr);}
  scroll.scrollTop=scrollTop;scroll.scrollLeft=scrollLeft;
}
async function reloadSearch(){if(criteria){result=await request(searchPath(criteria));renderSearch();}}
async function sourceAction(name){renderSource(await request('acquisition/'+name,'POST'));if(name==='reset')await reloadSearch();}
$('start').onclick=()=>act(()=>sourceAction('start'));
$('stop').onclick=()=>act(()=>sourceAction('stop'));
$('reset').onclick=()=>{$('reset-confirm').hidden=false;};
$('keep-demo').onclick=()=>{$('reset-confirm').hidden=true;};
$('confirm-reset').onclick=()=>act(async()=>{await sourceAction('reset');$('reset-confirm').hidden=true;selected=null;forcedWarning=false;renderSearch();if(user)await loadBookings();message('Исходные измерения и брони восстановлены.',true);});
async function poll(){if(busy||polling)return;polling=true;const version=revision;
  try{const data=await request('acquisition');if(version!==revision||busy)return;if(disconnected){message();disconnected=false;}renderSource(data);}
  catch(error){if(version===revision&&!busy){disconnected=true;source=null;controls();$('status').textContent='Нет связи с сервером';$('clock').textContent='Модельное время недоступно';if(result){result=null;selected=null;cardsKey='';$('rooms').replaceChildren();$('room-details').hidden=true;$('search-summary').textContent='Не удалось обновить результаты. Повторите поиск.';}message(error.message);}}
  finally{polling=false;}
}
poll();setInterval(poll,1500);
