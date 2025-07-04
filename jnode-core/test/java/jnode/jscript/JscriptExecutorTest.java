package jnode.jscript;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import javax.script.Bindings;
import javax.script.SimpleBindings;

public class JscriptExecutorTest {

    @Test
    public void testExecScript() throws Exception {

        Bindings bindings = new SimpleBindings();
        final JScriptConsole jScriptConsole = new JScriptConsole();
        bindings.put("console", jScriptConsole);

        JscriptExecutor.execScript("var a = 42 + 'ggg'; console.log(a);", bindings);
        assertEquals("42ggg", jScriptConsole.out());
    }

}