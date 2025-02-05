package org.acme.hibernate.orm;

import com.dylibso.chicory.runtime.ImportValues;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.wasi.WasiOptions;
import com.dylibso.chicory.wasi.WasiPreview1;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;

@ApplicationScoped
public class JavascriptService {

    private static final Logger LOGGER = Logger.getLogger(JavascriptService.class.getName());

    private String currentCode;

    public void installFunction(String code) {
        currentCode = code;
    }

    public String compute(String name) {
        try (
                ByteArrayInputStream stdin = new ByteArrayInputStream("".getBytes(UTF_8));
                var stdout = new ByteArrayOutputStream();
                var wasi = WasiPreview1.builder().withOptions(
                        WasiOptions.builder()
                                .withStdout(stdout)
                                .withStderr(stdout)
                                .withStdin(stdin)
                                .build()
                    ).build();
        ) {
            var quickjs =
                    Instance.builder(QuickJSModule.load())
                            .withMachineFactory(QuickJSModule::create)
                            .withImportValues(
                                    ImportValues.builder().addFunction(wasi.toHostFunctions()).build())
                            .build();

            String finalCode = "console.error(eval(`const name=\"" + name + "\";" + currentCode + "`));";
            LOGGER.warn("going to execute:\n" + finalCode);
            byte[] jsCode = finalCode.getBytes(UTF_8);
            var ptr =
                    quickjs.export("canonical_abi_realloc")
                            .apply(
                                    0, // original_ptr
                                    0, // original_size
                                    1, // alignment
                                    jsCode.length // new size
                            )[0];

            quickjs.memory().write((int) ptr, jsCode);
            var aggregatedCodePtr = quickjs.export("compile_src").apply(ptr, jsCode.length)[0];

            var codePtr = quickjs.memory().readI32((int) aggregatedCodePtr); // 32 bit
            var codeLength = quickjs.memory().readU32((int) aggregatedCodePtr + 4);

            quickjs.export("eval_bytecode").apply(codePtr, codeLength);

            String result = stdout.toString(UTF_8);
            LOGGER.info("JS UDF computed, result is: " + result);
            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
