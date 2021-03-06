signature JSON_CONTROLLER_PROTOCOL =
sig
  val run_in_textstreams : TextIO.instream * TextIO.outstream -> unit 
  val run_in_stdio : unit -> unit
  val parallel_run_in_textstreams : TextIO.instream * TextIO.outstream -> unit 
  val parallel_run_in_stdio : unit -> unit
end

structure JsonControllerProtocol =
struct

open JsonControllerUtil

structure JobTable = NameTable(structure Dom = IntName)

fun json_error rid code msg =
  Json.mk_object [
    ("request_id", Json.Int rid),
    ("success",    Json.Bool false),
    ("output",     Json.mk_object[("message", Json.String msg), ("code", Json.Int code)])
  ]

fun help x =
let
  val controller = arg_str x "controller"
  val module = arg_str x "module"
  val opt_function = optarg_str x "function"
  val mtab = case Symtab.lookup JsonControllerRegistry.ctab controller of SOME x => x
                  | NONE => raise user_exn ("Controller not found: " ^ controller)
  val modl = case Symtab.lookup mtab module of SOME x => x
                  | NONE => raise user_exn ("Module not found: " ^ module)
in
  case opt_function
    of NONE => Json.String (snd modl)
     | SOME function => (
         let
           val fd = snd (case Symtab.lookup (fst modl) function of SOME x => x
                         | NONE => raise user_exn ("Function not found: " ^ module ^ "." ^ function))
         in Json.String (
              module ^ "." ^ #name fd ^ ": " ^
              string_of_pargs (#input fd) ^ " -> " ^
              string_of_pargs (#output fd) ^ "\n  " ^
              #doc fd
            )
         end)
end

fun version _ = Json.String "2.1"

fun dispatch systemf rid json = let
  val controller = arg_str json "controller"
  val module = arg_str json "module"
  val function = arg_str json "function"
  val json_input = case optarg_json json "input" of SOME x => x | NONE => Json.mk_object []
in
  Json.mk_object [
    ("request_id", Json.Int rid),
    ("success",    Json.Bool true),
    ("output",     if controller = "!!" then
                     (case module of "system" => systemf function json_input
                                   | _ => raise user_exn "Module for !! controller must be 'system'.")
                   else JsonControllerRegistry.dispatch ((controller,module,function),json_input))
  ]
end
handle user_exn msg => json_error rid 0 msg
     | protocol_exn msg => json_error rid ~1 msg


fun run_in_textstreams (instream, outstream) =
let
  (* system function for dispatch *)
  fun system "help" x = help x
    | system "version" x = version x
    | system _ _ = raise user_exn ("Unrecognized system function. (options: help, version)")
  fun protocol_loop src =
  let
    val (json_list, src') = Source.get src
    val json = case json_list of [x] => x | _ =>
      raise protocol_exn "Expected: exactly one JSON object"
    val rid = arg_int json "request_id"
    val json_output = dispatch system rid json
  in (TextIO.output (outstream, Json.encode json_output);
      TextIO.flushOut outstream;
      protocol_loop src')
  end
in protocol_loop (Json.json_source instream)
end
handle protocol_exn msg => 
  (TextIO.output (outstream,
    Json.encode (Json.mk_object [
      ("request_id", Json.Int ~1),
      ("success",    Json.Bool false),
      ("output",     Json.mk_object [("message", Json.String msg), ("code", Json.Int ~1)])
    ]));
   TextIO.flushOut outstream)


fun parallel_run_in_textstreams (instream, outstream) =
let
  val output_buffer: (Json.json list) Unsynchronized.ref = Unsynchronized.ref []
  val output_lock = Mutex.mutex ()
  val has_output = ConditionVar.conditionVar ()
  val job_table: ((Json.json Future.future) JobTable.T) Synchronized.var =
    Synchronized.var "job_table" JobTable.empty
  
  (* system function for dispatch *)
  fun system "help" x = help x
    | system "kill" x =
      let
        val job = arg_int x "job"
      in 
         (case JobTable.get_opt (Synchronized.value job_table) job
            of SOME f => Future.cancel f
             | NONE   => raise user_exn ("No such job: " ^ Int.toString job));
         Json.mk_object [
           ("message", Json.String ("Job " ^ Int.toString job ^ " killed.")),
           ("job", Json.Int job)]
      end
    | system "version" x = version x
    | system _ _ = raise user_exn ("Unrecognized system function. (options: help, kill, version)")
  
  fun push_output json = (
    (*TextIO.output(outstream,"push_output: waiting for lock\n");*)
    Mutex.lock output_lock;
    (*TextIO.output(outstream,"push_output: obtained lock\n");*)
    output_buffer := (json :: !output_buffer);
    ConditionVar.broadcast has_output;
    Mutex.unlock output_lock(*;
    TextIO.output(outstream,"push_output: released lock\n")*)
  )
  fun flush_all buf = fold_rev (fn json => fn () => TextIO.output (outstream, Json.encode json)) buf ()
  fun output_loop () = (
    (*TextIO.output(outstream,"output_loop: waiting for lock\n");*)
    Mutex.lock output_lock;
    (*TextIO.output(outstream,"output_loop: obtained lock\n");*)
    if List.null (!output_buffer) then ConditionVar.wait (has_output, output_lock) else ();
    flush_all (!output_buffer);
    output_buffer := [];
    Mutex.unlock output_lock;
    (*TextIO.output(outstream,"output_loop: released lock\n");*)
    TextIO.flushOut outstream;
    output_loop ()
  )

  fun input_loop src =
  let
    val (json_list, src') = Source.get src
    val json = case json_list of [x] => x | _ =>
      raise protocol_exn "Expected: exactly one JSON object"
    val rid = arg_int json "request_id"
    val _ = Synchronized.change job_table (fn tab =>
      if IntName.NSet.contains (JobTable.get_dom_set tab) rid
      then (push_output (json_error rid 0 "Duplicate request ID"); tab)
      else
        let val worker = Future.fork (fn () => dispatch system rid json)
        in Future.fork (fn () => (
              let
                val json = PolyML.exception_trace (fn () => (
                             Future.join worker
                             handle Thread.Interrupt => json_error rid 1 "Job interrupted"
                           )) handle _ => json_error rid 2 "Unexpected exception"
              in
                push_output json; 
                Synchronized.change job_table (JobTable.delete rid)
              end
           ));
           JobTable.add (rid,worker) tab
        end)
  in input_loop src'
  end
in (
    Future.fork output_loop;
    input_loop (Json.json_source instream)
   )
end
handle protocol_exn msg => 
  (TextIO.output (outstream,
    Json.encode (Json.mk_object [
      ("request_id", Json.Int ~1),
      ("success",    Json.Bool false),
      ("output",     Json.mk_object [("message", Json.String msg), ("code", Json.Int ~1)])
    ]));
   TextIO.flushOut outstream)


fun flushIn () = case TextIO.canInput (TextIO.stdIn, 1)
                   of SOME _ => (TextIO.input1 TextIO.stdIn;())
                    | NONE => ()

fun run_in_stdio () = (run_in_textstreams (TextIO.stdIn,TextIO.stdOut))
fun parallel_run_in_stdio () = (parallel_run_in_textstreams (TextIO.stdIn,TextIO.stdOut))


end