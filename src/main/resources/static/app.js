'use strict';
const buttons = Object.fromEntries(['start', 'stop', 'reset'].map(id => [id, document.getElementById(id)]));
let state;
let busy = false;
let polling = false;
let revision = 0;
let disconnected = false;
const number = (value, digits = 2) => Number(value).toLocaleString('ru-RU', {minimumFractionDigits: digits, maximumFractionDigits: digits});

async function request(path = '', method = 'GET', body) {
  const response = await fetch('/api/acquisition' + path, {
    method,
    headers: body ? {'Content-Type': 'application/json'} : {},
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(5000)
  });
  const data = await response.json();
  if (!response.ok) throw new Error(data.message || `Ошибка сервера: ${response.status}`);
  return data;
}

function showError(message) {
  const element = document.getElementById('message');
  element.textContent = message || '';
  element.hidden = !message;
}

function updateControls() {
  const running = Boolean(state?.running);
  buttons.start.disabled = busy || !state || running || state.completed;
  buttons.stop.disabled = busy || !state || !running;
  buttons.reset.disabled = busy || !state || running;
}

function render(data) {
  state = data;
  const status = document.getElementById('status');
  status.textContent = data.running ? 'Идёт чтение файла' : data.completed ? 'Файл прочитан. Для повтора нажмите «Сброс».' : 'Чтение остановлено';
  status.classList.toggle('running', data.running);
  const settings = data.settings;
  document.getElementById('settings-info').textContent = `Интервал чтения: ${number(settings.samplePeriodMs / 1000, 1)} с. Шаг округления: ${number(settings.quantumPercent, 1)}. Окно среднего: ${settings.averagingWindow}. Размер таблицы: ${settings.bufferCapacity}.`;
  const tbody = document.getElementById('measurements');
  tbody.replaceChildren();
  for (const item of data.measurements) {
    const row = document.createElement('tr');
    for (const value of [item.sequence, number(item.elapsedSeconds, 0), number(item.sourceValue, 1), number(item.rawValue, 0), number(item.filteredValue)]) {
      const cell = document.createElement('td');
      cell.textContent = value;
      row.appendChild(cell);
    }
    tbody.appendChild(row);
  }
  if (!data.measurements.length) {
    const row = tbody.insertRow();
    const cell = row.insertCell();
    cell.colSpan = 5;
    cell.className = 'empty';
    cell.textContent = 'Нажмите «Старт», чтобы получить данные.';
  }
  document.getElementById('source-info').textContent = `Источник: ${data.sourceName}. Записей в файле: ${data.totalRecords}. Аудитория: ${data.roomId}.`;
  if (data.error) showError(data.error);
  updateControls();
}

async function act(path, method = 'POST', body) {
  if (busy) return;
  busy = true;
  revision++;
  updateControls();
  showError('');
  try { render(await request(path, method, body)); }
  catch (error) { showError(error.message); }
  finally { busy = false; updateControls(); }
}

buttons.start.addEventListener('click', () => act('/start'));
buttons.stop.addEventListener('click', () => act('/stop'));
buttons.reset.addEventListener('click', () => act('/reset'));

async function refresh() {
  if (busy || polling) return;
  polling = true;
  const requestedRevision = revision;
  try {
    const data = await request();
    // Ответ старого опроса не должен затереть результат нажатой пользователем кнопки.
    if (!busy && requestedRevision === revision) {
      if (disconnected) showError('');
      disconnected = false;
      render(data);
    }
  } catch (error) {
    if (!busy && requestedRevision === revision) {
      disconnected = true;
      state = undefined;
      document.getElementById('status').textContent = 'Нет связи с сервером';
      document.getElementById('status').classList.remove('running');
      showError('Не удалось получить данные. Проверьте, запущено ли приложение.');
      updateControls();
    }
  } finally { polling = false; }
}

(async () => {
  await refresh();
  setInterval(refresh, 500);
})();
