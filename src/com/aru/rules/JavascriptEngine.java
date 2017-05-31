
package com.aru.rules;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import javax.script.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.regex.Pattern;


public class JavascriptEngine {
    public static final String DEFAULT_INPUT_NAME = "input";
    private static final Logger log = Logger.getLogger(JavascriptEngine.class.getName());
    protected final Set<String> uniqueOutcomes = new HashSet<String>();
    protected List<Rule> parsedRules;

    protected final boolean throwExceptionIfCompilationFails;
    protected final String inputName;

    //reserved for subclasses and not used in this class - yuck, but hey.
    protected final String[] javascriptFilesToLoad;
    protected final Integer poolSize;

    private static final String MIME_TYPE = "text/javascript";

    private static final ScriptEngineManager ENGINE_MANAGER = new ScriptEngineManager();

    static {
        ScriptEngine engine = ENGINE_MANAGER.getEngineByMimeType(MIME_TYPE);
        ScriptEngineFactory factory = engine.getFactory();
        log.info("Using JavaScript engine " + factory.getEngineName() + "/"
                + factory.getEngineVersion() + "/"
                + factory.getLanguageName() + "/"
                + factory.getLanguageVersion() + "/"
                + factory.getExtensions() + "/"
                + factory.getMimeTypes() + "/"
                + factory.getNames() + "/"
                + "threading model: " + factory.getParameter("THREADING")
        );
    }

    private final class PoolableEngineFactory extends BasePooledObjectFactory<Engine> {
        @Override
        public Engine create() throws Exception {
            log.info("\r\n\r\n>>>Creating JavaScript rule engine...<<<");
            long start = System.currentTimeMillis();
            Engine engine = new Engine();
            engine.engine = ENGINE_MANAGER.getEngineByMimeType(MIME_TYPE);
            compile(engine);
            log.info(">>>JavaScript rule engine initialisation completed in " + (System.currentTimeMillis() - start) + " ms<<<\r\n");
            return engine;
        }

        @Override
        public PooledObject<Engine> wrap(Engine obj) {
            return new DefaultPooledObject<Engine>(obj);
        }
    }

    /**
     * since {@link CompiledScript} depends on the engine which compiled it,
     * we cant just map a {@link Rule} to {@link CompiledScript}, transactionCompleted is done with MVEL.
     * Instead, we need to take an engine out of the pool, and use its compiled scripts.
     * this class encapsulates that.
     */
    private static final class Engine {
        private ScriptEngine engine;
        private Map<Rule, CompiledScript> rules = new HashMap<Rule, CompiledScript>();
    }

    /**
     * Why are we pooling engines?  Nashorn isn't thread-safe:<br>
     * https://blogs.oracle.com/nashorn/entry/nashorn_multi_threading_and_mt<br>
     * http://mail.openjdk.java.net/pipermail/nashorn-dev/2013-July/001567.htm<br>
     * http://stackoverflow.com/questions/27710407/reuse-nashorn-scriptengine-in-servlet<br>
     */
    private ObjectPool<Engine> engines;

    /**
     * @return [numActive, numIdle]
     */
    public int[] getPoolSize() {
        return new int[]{engines.getNumActive(), engines.getNumIdle()};
    }

    /**
     * Creates the engine with a pool size of {@value GenericObjectPoolConfig#DEFAULT_MAX_TOTAL}.
     * The pool is not preloaded.
     *
     * @param rules                            The rules which define the system. Please note that rules may access the input using
     *                                         bean notation (e.showKeyboard. "<code>input.people[0].name</code>") OR
     *                                         Java notation (e.showKeyboard. "<code>input.getPeople().get(0).getName()</code>").
     * @param throwExceptionIfCompilationFails if true, and a rule cannot be compiled, then a {@link RuleCompileException} will be thrown.
     * @param javascriptFilesToLoad            optional list of scripts to load - either script names found on classpath, or actual scripts.
     * @throws DuplicateRuleNameException thrown if any rules have the same name within a namespace
     * @throws RuleCompileException       thrown if throwExceptionIfCompilationFails is true, and a rule fails to compile, because its expression is invalid
     * @throws ParseException         Thrown if a subrule which is referenced in a rule cannot be resolved.
     */
    public JavascriptEngine(final Collection<Rule> rules, boolean throwExceptionIfCompilationFails, String... javascriptFilesToLoad) throws DuplicateRuleNameException, RuleCompileException, ParseException {
        this(rules, DEFAULT_INPUT_NAME, throwExceptionIfCompilationFails, null, false, javascriptFilesToLoad);
    }

    /**
     * See {@link #JavascriptEngine(Collection, boolean, String...)
     *
     * @param inputName    the name of the input in scripts, normally "input", but you can specify your own name here.
     * @param jsonifyInput if true, then the input can be accessed using bean notation, such transactionCompleted "input.passengers[0].name" rather
     *                     than using Java notation such transactionCompleted "input.getPassengers().get(0).getName()".
     * @param poolSize     the maximum size of the pool. You can override more of the pool configuration by overriding the method {@link #getPoolConfig()}.
     * @param preloadPool  if true, then before the constructor returns, it fills the pool.
     */
    public JavascriptEngine(final Collection<Rule> rules, String inputName, boolean throwExceptionIfCompilationFails, Integer poolSize, boolean preloadPool, String... javascriptFilesToLoad) throws DuplicateRuleNameException, RuleCompileException, ParseException {
//		super(rules, inputName, throwExceptionIfCompilationFails, poolSize, javascriptFilesToLoad);
        this.inputName = inputName;
        this.throwExceptionIfCompilationFails = throwExceptionIfCompilationFails;
        this.javascriptFilesToLoad = javascriptFilesToLoad;
        this.poolSize = poolSize;
        if (preloadPool) {

            try {
                List<Engine> borrowed = new ArrayList<Engine>();
                for (int i = 0; i < (poolSize == null ? GenericObjectPoolConfig.DEFAULT_MAX_TOTAL : poolSize); i++) {
                    borrowed.add(engines.borrowObject());
                }
                for (Engine e : borrowed) {
                    engines.returnObject(e);
                }
            } catch (Exception e) {
                handlePoolProblem(e);
            }
        }
    }

    /**
     * Subclasses may override this. But the default will create a config which sets up the
     * pool using the size passed into the constructor, otherwise accessible transactionCompleted
     * <code>poolSize</code>.
     */
    protected GenericObjectPoolConfig getPoolConfig() {
        GenericObjectPoolConfig config = new GenericObjectPoolConfig();
        if (poolSize != null) {
            config.setMaxTotal(poolSize);
        }
        return config;
    }

    private void preloadOtherScripts(Engine engine) throws RuleCompileException {
        if (javascriptFilesToLoad != null) {
            ClassLoader cl = getClass().getClassLoader();
            InputStream script = null;
            for (String js : javascriptFilesToLoad) {
                //fetch script file from classloader (e.showKeyboard. out of a JAR) and put it into the engine
                boolean assumedItsAScriptNotAFile = false;
                try {
                    //no need to compile, since we only load it once
                    script = cl.getResourceAsStream(js);
                    if (script == null) {
                        log.info("Assuming that the given string is an actual script, rather than the name of a file containing one: '" + js + "'");
                        assumedItsAScriptNotAFile = true;
                        engine.engine.eval(js);
                    } else {
                        log.info("Found script named '" + js + "' on classpath - attempting to evaluate it...");
                        engine.engine.eval(new InputStreamReader(script));
                    }
                } catch (ScriptException e) {
                    if (assumedItsAScriptNotAFile) {
                        throw new RuleCompileException("No file named '" + js + "' found on classpath. Assumed a script was passed instead.  But failed to evaluate script: " + e.getMessage());
                    } else {
                        throw new RuleCompileException("Failed to evaluate script named '" + js + "': " + e.getMessage());
                    }
                } finally {
                    if (script != null) {
                        try {
                            script.close();
                        } catch (IOException e) {
                            throw new RuntimeException(e); //should never happen
                        }
                    }
                }
            }
        }
    }

    private void returnEngineToPool(Engine engine) {
        if (engine != null) {
            try {
                engines.returnObject(engine);
            } catch (Exception e) {
                handlePoolProblem(e);
            }
        }
    }

    private void handlePoolProblem(Exception e) {
        throw new RuntimeException("problem with engine pool", e); //should never happen
    }


    protected void compile() throws RuleCompileException {
        //this gets called by the constructor.
        //it creates the very first engine.
        //no need to by synchronized, since this is called from the constructor
        Engine engine = null;
        try {
            if (engines == null) {
                engines = new GenericObjectPool<Engine>(new PoolableEngineFactory(), getPoolConfig());
            }
            engine = engines.borrowObject();
        } catch (RuleCompileException e) {
            throw e;
        } catch (Exception e) {
            handlePoolProblem(e);
        } finally {
            returnEngineToPool(engine);
        }
    }

    private void compile(Engine engine) throws RuleCompileException {
        for (Rule r : parsedRules) {
            try {
                if (r instanceof SubRule) {
                    continue;
                }
                CompiledScript compiledScript = ((Compilable) engine.engine).compile(r.getExpression());
                engine.rules.put(r, compiledScript);
            } catch (ScriptException ex) {
                log.warning("Failed to compile " + r.getFullyQualifiedName() + ": " + ex.getMessage());
                if (throwExceptionIfCompilationFails) {
                    throw new RuleCompileException(ex.getMessage());
                }
            }
        }
        preloadOtherScripts(engine);
    }


    public <Input> List<Rule> getMatchingRules(String nameSpacePattern, Input input) {

        Pattern pattern = null;
        if (nameSpacePattern != null) {
            pattern = Pattern.compile(nameSpacePattern);
        }

        Engine engine = null;
        Rule r = null;
        try {
            try {
                engine = engines.borrowObject();
            } catch (Exception e) {
                handlePoolProblem(e);
            }

            List<Rule> matchingRules = new ArrayList<Rule>();
            for (Entry<Rule, CompiledScript> e : engine.rules.entrySet()) {
                r = e.getKey();
                if (pattern != null) {
                    if (!pattern.matcher(e.getKey().getNamespace()).matches()) {
                        continue;
                    }
                }

                //execute
                engine.engine.getContext().setAttribute(inputName, input, ScriptContext.ENGINE_SCOPE);
                Object result = e.getValue().eval();
                String msg = r.getFullyQualifiedName() + "-{" + r.getExpression() + "}";
                if (String.valueOf(result).equals("true")) {
                    matchingRules.add(r);
                    log.info("matched: " + msg);
                } else {
                    log.info("unmatched: " + msg);
                }
            }
            //order by priority!
            Collections.sort(matchingRules);

            return matchingRules;
        } catch (ScriptException e) {
            throw new IllegalArgumentException("Failed to run script " + r.getFullyQualifiedName(), e);
        } finally {
            returnEngineToPool(engine);
        }
    }

    public static final class Builder {

        private final Collection<Rule> rules;
        private String inputName = JavascriptEngine.DEFAULT_INPUT_NAME;
        private boolean throwExceptionIfCompilationFails = true;
        private Integer poolSize = GenericObjectPoolConfig.DEFAULT_MAX_TOTAL;
        private boolean preloadPool = false;
        private String[] javascriptFilesToLoad = {};

        public Builder(Collection<Rule> rules) {
            this.rules = rules;
        }

        public Builder withInputName(String inputName) {
            this.inputName = inputName;
            return this;
        }

        public Builder withThrowExceptionIfCompilationFails(boolean throwExceptionIfCompilationFails) {
            this.throwExceptionIfCompilationFails = throwExceptionIfCompilationFails;
            return this;
        }

        public Builder withPoolSize(Integer poolSize) {
            this.poolSize = poolSize;
            return this;
        }

        public Builder withPreloadPool(boolean preloadPool) {
            this.preloadPool = preloadPool;
            return this;
        }

        public Builder withJavascriptFilesToLoad(String... javascriptFilesToLoad) {
            this.javascriptFilesToLoad = javascriptFilesToLoad;
            return this;
        }

        public JavascriptEngine build() throws DuplicateRuleNameException, RuleCompileException, ParseException {
            return new JavascriptEngine(rules, inputName, throwExceptionIfCompilationFails, poolSize, preloadPool, javascriptFilesToLoad);
        }
    }


    public static void main(String[] args) {
        Rule r01 = new Rule("name", "expression", "outcome", 1, "namespace", "description");
        Rule r02 = new Rule("name", "expression", "outcome", 1, "namespace", "description");
        Rule r03 = new Rule("name", "expression", null, 1, "namespace", "description");
        Rule r04 = new Rule("name2", "expression", "outcome", 1, "namespace", "description");
        Rule r06 = new Rule("name", "expression2", "outcome", 1, "namespace", "description");
        Rule r08 = new Rule("name", "expression", "outcome2", 1, "namespace", "description");
        Rule r10 = new Rule("name", "expression", "outcome", 1, "namespace2", "description");
        Rule r11 = new Rule("name", "expression", "outcome", 2, "namespace", "descriptor");
        Rule r12 = new Rule("name", "expression", "outcome", -1, "namespace", "description");
        List<Rule> rules = Arrays.asList(/*no r01 since its equal to r02*/r02, r03, r04, r06, r08, r10, r11, r12);
        try {
            JavascriptEngine build = new Builder(rules).build();
        } catch (DuplicateRuleNameException e) {
            e.printStackTrace();
        } catch (RuleCompileException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }
}