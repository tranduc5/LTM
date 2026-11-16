const $ = id => document.getElementById(id);
let ws = null, nick = '', room = '', loggedIn = false, token = '', mode = 'ROOM', peer = '', signup = false;
const roomMessages = new Map(), pmMessages = new Map();
const HELP = 'Lệnh: /join <phòng> · /leave · /rooms · /users · /pm <tên> <nội dung> · /quit';
function send(line) { if (ws && ws.readyState === WebSocket.OPEN) ws.send(line); }
function hhmm() { return new Date().toTimeString().slice(0, 5); }
function safe(s) { return String(s || '').replace(/[\t\r\n]/g, ' '); }
function b64(s) { const bytes = new TextEncoder().encode(s); let bin=''; for(const b of bytes) bin+=String.fromCharCode(b);return btoa(bin); }
function where() { return mode === 'ROOM' ? room : peer.toLowerCase(); }
function key() { return mode === 'ROOM' ? 'room:'+room : 'pm:'+peer.toLowerCase(); }
function addElement(kind,who,text,time,extra={}) {
  const d=document.createElement('div');d.className=kind==='sys'?'sys':kind==='err'?'err':'msg'+(who.toLowerCase()===nick.toLowerCase()?' me':'');
  if(kind==='sys'||kind==='err'){d.textContent=text;return d;}
  const w=document.createElement('span');w.className='who';w.textContent=who;
  const t=document.createElement('span');t.className='time';t.textContent=time||hhmm();w.appendChild(t);d.appendChild(w);
  if(extra.fileId){
    const a=document.createElement('a');a.href='#';a.className='download';a.textContent='📎 '+text+' (Tải về)';
    a.onclick=async e=>{e.preventDefault();await download(extra.fileId,text,a);};d.appendChild(a);
    if(/\.(png|jpe?g|gif|webp)$/i.test(text)){
      const img=document.createElement('img');img.className='attachment-image';img.alt=text;img.loading='lazy';d.appendChild(img);
      fetch('/files/'+extra.fileId,{headers:{Authorization:'Bearer '+token}}).then(r=>{if(!r.ok)throw Error('Không có quyền');return r.blob();}).then(blob=>{const url=URL.createObjectURL(blob);img.src=url;img.onload=()=>URL.revokeObjectURL(url);}).catch(()=>img.remove());
    }
  }else{const span=document.createElement('span');span.textContent=text;d.appendChild(span);}
  return d;
}
async function download(id,name,link){try{const r=await fetch('/files/'+id,{headers:{Authorization:'Bearer '+token}});if(!r.ok)throw Error('Tải file thất bại');const blob=await r.blob(),url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=name;document.body.appendChild(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),30000);}catch(e){link.textContent=e.message;}}
function renderLog() { const log=$('log');log.replaceChildren();for(const item of (mode==='ROOM'?roomMessages.get(room)||[]:pmMessages.get(peer.toLowerCase())||[])) log.appendChild(addElement(item.kind,item.who,item.text,item.time,item.extra));log.scrollTop=log.scrollHeight; }
function add(target,item) { const m=target.startsWith('room:')?roomMessages:pmMessages,k=target.slice(target.indexOf(':')+1);if(!m.has(k))m.set(k,[]);m.get(k).push(item);if(m.get(k).length>200)m.get(k).shift();if(target===key())renderLog(); }
function note(text,error=false){if(!loggedIn)return;add(key(),{kind:error?'err':'sys',who:'',text,time:''});}
function chooseRoom(name){mode='ROOM';room=name;peer='';$('roomTitle').textContent='# '+name;$('roomSubtitle').textContent='Phòng công khai';markActive();renderLog();}
function choosePM(name){if(name.toLowerCase()===nick.toLowerCase())return;mode='PM';peer=name;$('roomTitle').textContent='💬 '+name;$('roomSubtitle').textContent='Tin nhắn riêng';markActive();pmMessages.set(name.toLowerCase(),[]);send('PMHIST '+name);renderLog();$('msg').focus();}
function connect(name,password){
  const previous=ws;
  const path=(location.protocol==='https:'?'wss://':'ws://')+location.host+'/ws';
  $('loginErr').textContent='';
  const socket=new WebSocket(path);
  ws=socket;
  // Phần đóng kết nối cũ không được báo nhầm là lỗi trên kết nối mới.
  if(previous && previous.readyState<=1) previous.close();
  const action=signup?'REGISTER ':'LOGIN ';
  socket.onopen=()=>{if(ws===socket)socket.send(action+name+' '+password);};
  socket.onmessage=e=>{if(ws===socket)handle(e.data);};
  socket.onerror=()=>{
    if(ws===socket && !loggedIn) $('loginErr').textContent='Không mở được WebSocket. Kiểm tra cửa sổ Run của NetBeans và cổng TCP 5000.';
  };
  socket.onclose=()=>{
    if(ws!==socket)return; // Đóng do đăng ký thành công hoặc chuyển kết nối: không báo lỗi.
    ws=null;
    if(loggedIn){
      $('msg').disabled=true;
      note('Mất kết nối tới Server. Vui lòng tải lại trang.',true);
    }else $('loginErr').textContent||='Kết nối WebSocket đã đóng. Kiểm tra Server và cổng TCP 5000.';
  };
}
function handle(line){const f=line.split('\t');switch(f[0]){
case 'REGISTEROK':{signup=false;tabs();$('loginErr').textContent='Đăng ký thành công! Hãy chọn Đăng nhập với tài khoản vừa tạo.';const registered=ws;ws=null;if(registered)registered.close();break;}
case 'LOGINOK':nick=f[1];token=f[2];loggedIn=true;$('login').style.display='none';$('app').style.display='grid';$('myName').textContent=nick;$('me').textContent='Bạn: '+nick;send('WATCH');send('PEOPLE');$('msg').focus();break;
case 'NICKOK':break;
case 'ERROR':if(!loggedIn){$('loginErr').textContent=f[1]||'Lỗi đăng nhập';}else note(f[1]||'Lỗi',true);break;
case 'INFO':if(loggedIn)note(f[1]);break;
case 'JOINED':room=f[1];roomMessages.set(room,[]);chooseRoom(room);break;
case 'SYSTEM':if(f[1]===room&&mode==='ROOM')note(f[2]);break;
case 'CHAT':add('room:'+f[1],{kind:'msg',who:f[2],text:f.slice(3).join('\t'),time:hhmm()});break;
case 'HISTORY':add('room:'+f[1],{kind:'msg',who:f[3],text:f.slice(4).join('\t'),time:f[2]});break;
case 'PM':{const other=f[1].toLowerCase()===nick.toLowerCase()?f[2]:f[1];add('pm:'+other.toLowerCase(),{kind:'msg',who:f[1],text:f.slice(3).join('\t'),time:hhmm()});send('PEOPLE');break;}
case 'PMCLEAR':pmMessages.set(f[1].toLowerCase(),[]);if(key()==='pm:'+f[1].toLowerCase())renderLog();break;
case 'PMHISTORY':{const other=f[1].toLowerCase()===nick.toLowerCase()?f[2]:f[1];add('pm:'+other.toLowerCase(),{kind:'msg',who:f[1],text:f[3],time:f[4]});break;}
case 'FILEMSG':{const isRoom=f[1]==='ROOM',target=isRoom?'room:'+f[2]:'pm:'+(f[3].toLowerCase()===nick.toLowerCase()?f[2]:f[3]).toLowerCase();add(target,{kind:'msg',who:f[3],text:f[5],time:f[6],extra:{fileId:f[4]}});break;}
case 'ROOMS':renderRooms(f[1]||'');break;
case 'USERS':if(f[1]===room)renderUsers(f[2]||'');break;
case 'PEOPLE':renderPeople(f[1]||'');break;
}}
function renderRooms(s){const ul=$('rooms');ul.replaceChildren();s.split(',').filter(Boolean).forEach(r=>{const i=r.lastIndexOf(':'),name=r.slice(0,i),n=r.slice(i+1),li=document.createElement('li');li.dataset.room=name;li.textContent='# '+name;const c=document.createElement('span');c.className='count';c.textContent=n;li.appendChild(c);li.onclick=()=>send('JOIN '+name);ul.appendChild(li);});markActive();}
function markActive(){document.querySelectorAll('#rooms li').forEach(li=>li.classList.toggle('active',mode==='ROOM'&&li.dataset.room===room));document.querySelectorAll('#people li').forEach(li=>li.classList.toggle('active',mode==='PM'&&li.dataset.person===peer.toLowerCase()));}
function renderUsers(s){const ul=$('users');ul.replaceChildren();s.split(',').filter(Boolean).forEach(u=>{const li=document.createElement('li');li.textContent=u.toLowerCase()===nick.toLowerCase()?u+' (bạn)':u;if(u.toLowerCase()!==nick.toLowerCase())li.onclick=()=>choosePM(u);else li.className='self';ul.appendChild(li);});}
function renderPeople(s){const ul=$('people');ul.replaceChildren();s.split(',').filter(Boolean).forEach(v=>{const i=v.lastIndexOf(':'),person=v.slice(0,i),status=v.slice(i+1);if(person.toLowerCase()===nick.toLowerCase())return;const li=document.createElement('li');li.dataset.person=person.toLowerCase();const left=document.createElement('span'),dot=document.createElement('span');dot.className='dot '+(status==='online'?'online':'offline');left.append(dot,document.createTextNode(' '+person));li.append(left);li.onclick=()=>choosePM(person);ul.appendChild(li);});markActive();}
function tabs(){$('loginTab').classList.toggle('selected',!signup);$('registerTab').classList.toggle('selected',signup);$('loginSubmit').textContent=signup?'Tạo tài khoản':'Đăng nhập';$('loginErr').textContent='';$('password').autocomplete=signup?'new-password':'current-password';}
$('loginTab').onclick=()=>{signup=false;tabs();};$('registerTab').onclick=()=>{signup=true;tabs();};
$('loginForm').onsubmit=e=>{e.preventDefault();let n=$('nick').value.trim(),password=$('password').value;if(!/^[\p{L}\p{N}_-]{2,20}$/u.test(n)||password.length<8||password.includes(' ')){ $('loginErr').textContent='Tên 2–20 ký tự; mật khẩu >= 8 ký tự, không chứa dấu cách.';return;}connect(n,password);};
$('sendForm').onsubmit=e=>{e.preventDefault();const text=$('msg').value.trim();if(!text)return;$('msg').value='';if(text==='/help'){note(HELP);return;}if(text.startsWith('/')){const p=text.slice(1).split(/\s+/),cmd=p[0].toLowerCase();if(cmd==='join'&&p[1])send('JOIN '+p[1]);else if(cmd==='leave')send('LEAVE');else if(cmd==='rooms')send('ROOMS');else if(cmd==='users')send('USERS');else if(cmd==='pm'&&p.length>=3){choosePM(p[1]);send('PM '+p[1]+' '+p.slice(2).join(' '));}else if(cmd==='quit')logout();else note(HELP,true);}else send(mode==='ROOM'?'MSG '+safe(text):'PM '+peer+' '+safe(text));};
$('newRoom').onclick=()=>{const r=prompt('Tên phòng (chữ, số, _ hoặc -):');if(r&&/^[\p{L}\p{N}_-]{1,20}$/u.test(r.trim()))send('JOIN '+r.trim());};
function logout(){if(ws)ws.close();token='';location.reload();}$('logout').onclick=logout;
$('file').onchange=async()=>{const file=$('file').files[0];$('file').value='';if(!file)return;if(file.size>600000||file.size===0){note('File phải từ 1 byte đến 600 KB',true);return;}if(!/\.(png|jpe?g|gif|webp|pdf|txt|zip)$/i.test(file.name)){note('Không hỗ trợ định dạng này',true);return;}
  try {const buffer=await file.arrayBuffer(),bytes=new Uint8Array(buffer);let binary='';for(let i=0;i<bytes.length;i+=8192)binary+=String.fromCharCode(...bytes.subarray(i,i+8192));let target=mode==='ROOM'?room:peer;send('FILE '+mode+' '+target+' '+b64(file.name)+' '+b64(file.type||'application/octet-stream')+' '+btoa(binary));note('Đang gửi '+file.name+'...');}catch(e){note('Không đọc được file: '+e.message,true);}
};
