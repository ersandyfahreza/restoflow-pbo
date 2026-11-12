"""Integration tests against a disposable local PostgreSQL, never a Supabase project."""
from pathlib import Path
import json, os, socket, subprocess, time, uuid, ctypes
from types import SimpleNamespace

ROOT = Path(__file__).resolve().parent.parent
BIN = ROOT / '.tools/postgres/bin-dist/bin'
DATA = ROOT / '.tools/postgres/data'
RESULTS = []
FLAGS = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0

def run(args, source=None, check=True):
    result = subprocess.run([str(a) for a in args], input=source, text=True, encoding='utf-8',
                            capture_output=True, creationflags=FLAGS)
    if check and result.returncode:
        raise AssertionError(result.stderr or result.stdout)
    return result

def check(name, assertion):
    assert assertion, name
    RESULTS.append(name)
    print('PASS', name, flush=True)

def main():
    if not (BIN/'postgres.exe').exists():
        raise SystemExit('PostgreSQL test binary missing. See TESTING.md.')
    if not DATA.exists():
        run([BIN/'initdb.exe','-D',DATA,'-U','postgres','-A','trust','--encoding=UTF8','--locale=C'])
    with socket.socket() as sock:
        sock.bind(('127.0.0.1',0)); port = sock.getsockname()[1]
    db='rf_test_'+uuid.uuid4().hex
    dll_dir=os.add_dll_directory(str(BIN))
    pq=ctypes.CDLL(str(BIN/'libpq.dll'))
    signatures={
        'PQconnectdb':([ctypes.c_char_p],ctypes.c_void_p),
        'PQstatus':([ctypes.c_void_p],ctypes.c_int),'PQerrorMessage':([ctypes.c_void_p],ctypes.c_char_p),
        'PQexec':([ctypes.c_void_p,ctypes.c_char_p],ctypes.c_void_p),
        'PQresultStatus':([ctypes.c_void_p],ctypes.c_int),
        'PQresultErrorMessage':([ctypes.c_void_p],ctypes.c_char_p),
        'PQntuples':([ctypes.c_void_p],ctypes.c_int),
        'PQnfields':([ctypes.c_void_p],ctypes.c_int),
        'PQgetvalue':([ctypes.c_void_p,ctypes.c_int,ctypes.c_int],ctypes.c_char_p),
        'PQclear':([ctypes.c_void_p],None),'PQfinish':([ctypes.c_void_p],None)}
    for name,(args,returns) in signatures.items():getattr(pq,name).argtypes=args;getattr(pq,name).restype=returns
    def query(database,source,ok=True):
        connection=pq.PQconnectdb(f'host=127.0.0.1 port={port} dbname={database} user=postgres connect_timeout=2 client_encoding=UTF8'.encode())
        try:
            if pq.PQstatus(connection)!=0:
                if ok:raise AssertionError(pq.PQerrorMessage(connection).decode('utf-8',errors='replace'))
                return SimpleNamespace(returncode=1,stdout='',stderr='Database not ready')
            result=pq.PQexec(connection,source.encode('utf-8'))
            try:
                status=pq.PQresultStatus(result)
                error=pq.PQresultErrorMessage(result).decode('utf-8',errors='replace')
                if status not in (0,1,2):
                    if ok:raise AssertionError(error)
                    return SimpleNamespace(returncode=1,stdout='',stderr=error)
                text='\n'.join('|'.join(pq.PQgetvalue(result,i,j).decode('utf-8') for j in range(pq.PQnfields(result))) for i in range(pq.PQntuples(result)))
                return SimpleNamespace(returncode=0,stdout=text,stderr='')
            finally:pq.PQclear(result)
        finally:pq.PQfinish(connection)
    logpath=ROOT/'target/postgres-test.log';logpath.parent.mkdir(exist_ok=True)
    with logpath.open('w',encoding='utf-8') as log:
        server=subprocess.Popen([str(BIN/'postgres.exe'),'-D',str(DATA),'-h','127.0.0.1','-p',str(port)],stdout=log,stderr=log,creationflags=FLAGS)
        try:
            for _ in range(80):
                if query('postgres','SELECT 1',False).returncode==0:break
                if server.poll() is not None:raise AssertionError(logpath.read_text())
                time.sleep(.25)
            query('postgres',f'CREATE DATABASE {db}')
            def sql(source, ok=True):
                return query(db,source,ok)
            sql("""
              DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN CREATE ROLE anon; END IF;
              IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN CREATE ROLE authenticated; END IF; END $$;
              CREATE SCHEMA auth;
              CREATE TABLE auth.users(id uuid PRIMARY KEY);
              CREATE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql STABLE AS $$ SELECT nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
              GRANT USAGE ON SCHEMA auth,public TO authenticated,anon;
              GRANT EXECUTE ON FUNCTION auth.uid() TO authenticated,anon;
            """)
            for migration in sorted((ROOT/'supabase/migrations').glob('*.sql')):
                sql(migration.read_text(encoding='utf-8'))
                check('migration '+migration.name,True)
            check('exact original row counts',sql("SELECT (SELECT count(*) FROM customer)||','||(SELECT count(*) FROM staff_kitchen)||','||(SELECT count(*) FROM kurir)||','||(SELECT count(*) FROM pemesanan)").stdout.strip()=='65,35,30,65')
            check('reimport rejected without changing data',sql((ROOT/'supabase/migrations/001_original_data.sql').read_text(encoding='utf-8'),False).returncode!=0)
            import re,sqlite3
            original=(ROOT/'DATABASE RESTORAN.sql').read_text(encoding='utf-8-sig')
            original=re.sub(r'--[^\n]*','',original)
            memory=sqlite3.connect(':memory:');memory.row_factory=sqlite3.Row
            total_rows=0
            for table,cols,values in re.findall(r'INSERT INTO (\w+)\s*\((.*?)\)\s*VALUES\s*(.*?);',original,re.S):
                memory.execute(f'CREATE TABLE {table} ({cols})');memory.execute(f'INSERT INTO {table} ({cols}) VALUES {values}')
                columns=[c.strip() for c in cols.split(',')]
                source=[dict(row) for row in memory.execute(f'SELECT {cols} FROM {table}')]
                args=','.join("'"+c+"',"+c for c in columns)
                actual=json.loads(sql(f'SELECT jsonb_agg(jsonb_build_object({args})) FROM {table}').stdout)
                for row in actual:
                    if 'tanggal_pemesanan' in row:row['tanggal_pemesanan']=row['tanggal_pemesanan'].replace('T',' ')
                canonical=lambda rows:sorted(json.dumps(row,sort_keys=True,ensure_ascii=False) for row in rows)
                check('exact source values '+table,canonical(source)==canonical(actual));total_rows+=len(source)
            check('715 original rows preserved',total_rows==715)
            check('read-only verification script executes',sql((ROOT/'supabase/verify.sql').read_text(encoding='utf-8')).returncode==0)
            u={name:f'00000000-0000-0000-0000-{i:012d}' for i,name in enumerate(['admin','cashier','kitchen','waiter','courier','other_courier'],1)}
            for user_id in u.values():sql(f"INSERT INTO auth.users VALUES('{user_id}');")
            sql(f"""INSERT INTO profiles(id,display_name,role,id_staff,id_pelayan) VALUES
              ('{u['admin']}','Admin','admin',NULL,NULL),('{u['cashier']}','Cashier','cashier',NULL,NULL),
              ('{u['kitchen']}','Kitchen','kitchen',1,NULL),('{u['waiter']}','Waiter','waiter',NULL,'PLY-01'),
              ('{u['courier']}','Courier','courier',6,NULL),('{u['other_courier']}','Other','courier',7,NULL);""")
            def actor(user, query, ok=True):
                return sql(f"SET ROLE authenticated;SET \"request.jwt.claim.sub\"='{u[user]}';{query};",ok)
            def rpc(user,action,payload,ok=True):
                body=json.dumps(payload).replace("'","''")
                out=actor(user,f"SELECT rf_execute('{action}','{body}'::jsonb)",ok)
                return json.loads(out.stdout.strip().splitlines()[-1]) if ok else out
            def snap(user):
                return json.loads(actor(user,"SELECT rf_snapshot('2026-01-01','2026-12-31')").stdout.strip().splitlines()[-1])
            check('anonymous denied',sql("SET ROLE anon;SELECT rf_snapshot('2026-01-01','2026-12-31');",False).returncode!=0)
            check('cashier cannot edit roles',rpc('cashier','save_profile',{'user_id':u['kitchen'],'role':'admin'},False).returncode!=0)
            check('kitchen cannot edit price',rpc('kitchen','save_menu',{'id':1,'price':1},False).returncode!=0)
            check('kitchen cannot read payment rows',actor('kitchen','SELECT count(*) FROM pembayaran').stdout.strip().splitlines()[-1]=='0')
            check('courier sees no unrelated orders',len(snap('courier')['orders'])==0)
            check('legacy paid revenue',snap('admin')['stats']['revenue']==4293000)
            legacy=snap('admin')['orders']
            check('ten legacy differences',sum(o['total']!=sum(i['price']*i['quantity'] for i in o['items']) for o in legacy)==10)
            request={'request_id':str(uuid.uuid4()),'type':'dine_in','table_id':65,'notes':'integration test','address':'','waiter':'PLY-01','kitchen_id':1,'items':[{'menu_id':1,'quantity':2},{'menu_id':8,'quantity':1}]}
            created=rpc('cashier','create_order',request);oid=created['id']
            check('server calculates total',created['total']==56000)
            check('idempotent retry same order',rpc('cashier','create_order',request)['id']==oid)
            changed=dict(request,notes='different payload')
            check('idempotent key bound to payload',rpc('cashier','create_order',changed,False).returncode!=0)
            check('occupied table rejected',rpc('cashier','create_order',dict(request,request_id=str(uuid.uuid4())),False).returncode!=0)
            check('waiter sees assigned order',any(o['id']==oid for o in snap('waiter')['orders']))
            before=int(sql('SELECT count(*) FROM pembayaran').stdout.strip())
            bad=dict(request,request_id=str(uuid.uuid4()),type='takeaway',items=[{'menu_id':99999,'quantity':1}])
            check('invalid menu rejected',rpc('cashier','create_order',bad,False).returncode!=0)
            check('failed order rolled back payment',int(sql('SELECT count(*) FROM pembayaran').stdout.strip())==before)
            check('invalid transition rejected',rpc('cashier','order_status',{'id':oid,'status':'Selesai'},False).returncode!=0)
            rpc('kitchen','order_status',{'id':oid,'status':'Diproses'});rpc('kitchen','order_status',{'id':oid,'status':'Siap'})
            check('unpaid completion rejected',rpc('cashier','order_status',{'id':oid,'status':'Selesai'},False).returncode!=0)
            check('insufficient cash rejected',rpc('cashier','pay',{'id':oid,'method':'Cash','received':1},False).returncode!=0)
            check('cash change correct',rpc('cashier','pay',{'id':oid,'method':'Cash','received':60000})['change']==4000)
            check('duplicate payment denied',rpc('cashier','pay',{'id':oid,'method':'Cash','received':60000},False).returncode!=0)
            check('paid cancellation denied',rpc('cashier','order_status',{'id':oid,'status':'Dibatalkan'},False).returncode!=0)
            rpc('cashier','order_status',{'id':oid,'status':'Selesai'})
            rpc('admin','save_menu',{'id':1,'name':'Nasi Goreng Spesial','price':30000,'category':'Nasi','description':'test','active':True})
            check('price snapshot preserved after menu change',sql(f'SELECT harga_satuan_saat_transaksi FROM detail_pemesanan WHERE id_pemesanan={oid} AND id_menu=1').stdout.strip()=='25000')
            delivery=dict(request,request_id=str(uuid.uuid4()),type='delivery',address='Local test address',items=[{'menu_id':8,'quantity':1}])
            did=rpc('cashier','create_order',delivery)['id']
            rpc('cashier','assign_delivery',{'id':did,'courier_id':1})
            check('assigned courier can see order',any(o['id']==did for o in snap('courier')['orders']))
            check('other courier cannot see order',len(snap('other_courier')['orders'])==0)
            check('other courier cannot update order',rpc('other_courier','delivery_status',{'id':did,'status':'Diambil'},False).returncode!=0)
            check('not ready delivery rejected',rpc('courier','delivery_status',{'id':did,'status':'Diambil'},False).returncode!=0)
            rpc('kitchen','order_status',{'id':did,'status':'Diproses'});rpc('kitchen','order_status',{'id':did,'status':'Siap'})
            rpc('cashier','pay',{'id':did,'method':'QRIS','received':6000})
            rpc('courier','delivery_status',{'id':did,'status':'Diambil'});rpc('courier','delivery_status',{'id':did,'status':'Terkirim'})
            check('delivered order completed',next(o for o in snap('admin')['orders'] if o['id']==did)['status']=='Selesai')
            edit=dict(request,request_id=str(uuid.uuid4()),type='takeaway',items=[{'menu_id':8,'quantity':1}])
            eid=rpc('cashier','create_order',edit)['id']
            rpc('cashier','update_order',dict(edit,id=eid,items=[{'menu_id':8,'quantity':2}]))
            check('unpaid waiting order editable',next(o for o in snap('admin')['orders'] if o['id']==eid)['total']==12000)
            check('direct profile update blocked',actor('cashier',f"UPDATE profiles SET role='admin' WHERE id='{u['cashier']}'",False).returncode!=0)
            check('self admin lockout prevented',rpc('admin','save_profile',{'user_id':u['admin'],'role':'cashier'},False).returncode!=0)
            check('zero quantity blocked',rpc('cashier','create_order',dict(edit,request_id=str(uuid.uuid4()),items=[{'menu_id':8,'quantity':0}]),False).returncode!=0)
            check('negative quantity blocked',rpc('cashier','create_order',dict(edit,request_id=str(uuid.uuid4()),items=[{'menu_id':8,'quantity':-1}]),False).returncode!=0)
            # Test all administration routes and concurrent retry/payment behaviour.
            mid=rpc('admin','save_menu',{'name':'Menu tes','price':5000,'category':'Tes','description':'test','active':True})['id']
            check('admin creates menu',int(mid)>65)
            check('negative price denied',rpc('admin','save_menu',{'name':'Bad','price':-1,'category':'Tes','active':True},False).returncode!=0)
            table=rpc('admin','save_table',{'name':'Meja tes','qr':'QR-TEST','active':True})['id']
            check('admin creates table',int(table)>65)
            check('duplicate QR denied',rpc('admin','save_table',{'name':'Other','qr':'QR-TEST','active':True},False).returncode!=0)
            staff_id=rpc('admin','save_staff',{'first':'Staf','last':'Tes','kind':'kitchen','active':True})['id']
            check('admin creates staff subtype',sql(f'SELECT count(*) FROM staff_kitchen WHERE id_staff={staff_id}').stdout.strip()=='1')
            rpc('admin','save_waiter',{'waiter_id':'PLY-TEST','first':'Pelayan','last':'Tes','active':True,'is_new':True})
            check('admin creates separate waiter',sql("SELECT count(*) FROM pelayan WHERE id_pelayan='PLY-TEST'").stdout.strip()=='1')
            rpc('admin','settings',{'name':'Restoran tes','footer':'Terima kasih'})
            check('settings persisted',snap('cashier')['settings']['display_name']=='Restoran tes')
            check('waiter cannot change settings',rpc('waiter','settings',{'name':'Bad','footer':''},False).returncode!=0)
            rpc('cashier','review',{'courier_id':1,'date':'2026-01-01','rating':5,'comment':'test'})
            check('review persisted',sql('SELECT count(*) FROM ulasan').stdout.strip()=='66')
            check('invalid rating denied',rpc('cashier','review',{'courier_id':1,'date':'2026-01-01','rating':6,'comment':'test'},False).returncode!=0)
            check('kitchen cannot change profiles',rpc('kitchen','save_profile',{'user_id':u['cashier'],'role':'admin'},False).returncode!=0)
            # Store mapping through API, then ensure deactivation applies to existing sessions.
            rpc('admin','save_profile',{'user_id':u['kitchen'],'name':'Kitchen','role':'kitchen','staff_id':1,'active':False})
            check('inactive account denied',actor('kitchen',"SELECT rf_snapshot('2026-01-01','2026-12-31')",False).returncode!=0)
            rpc('admin','save_profile',{'user_id':u['kitchen'],'name':'Kitchen','role':'kitchen','staff_id':1,'active':True})
            from concurrent.futures import ThreadPoolExecutor
            concurrent=dict(edit,request_id=str(uuid.uuid4()))
            with ThreadPoolExecutor(max_workers=2) as pool:
                results=list(pool.map(lambda _:rpc('cashier','create_order',concurrent),range(2)))
            check('concurrent idempotent create only one order',results[0]['id']==results[1]['id'])
            cid=results[0]['id'];payment={'id':cid,'method':'Cash','received':6000}
            with ThreadPoolExecutor(max_workers=2) as pool:
                results=list(pool.map(lambda _:rpc('cashier','pay',payment,False),range(2)))
            check('concurrent duplicate payment only one succeeds',sorted(r.returncode for r in results)==[0,1])
            (ROOT/'target/database-test-results.json').write_text(json.dumps({'passed':len(RESULTS),'tests':RESULTS},indent=2),encoding='utf-8')
            print(f'{len(RESULTS)} database checks passed.',flush=True)
        finally:
            query('postgres',f'DROP DATABASE IF EXISTS {db} WITH (FORCE)',False)
            run([BIN/'pg_ctl.exe','-D',DATA,'stop','-m','fast','-w'],check=False)
            try:server.wait(timeout=15)
            except subprocess.TimeoutExpired:server.terminate()

if __name__=='__main__':main()
