package org.unicode.cldr.unittest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.unicode.cldr.util.CLDRPaths;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.ibm.icu.dev.test.AbstractTestLog;
import com.ibm.icu.text.PersonName.FieldModifier;
import com.ibm.icu.text.PersonName.NameField;
import com.ibm.icu.text.PersonNameFormatter;
import com.ibm.icu.text.PersonNameFormatter.Formality;
import com.ibm.icu.text.PersonNameFormatter.Length;
import com.ibm.icu.text.PersonNameFormatter.Options;
import com.ibm.icu.text.PersonNameFormatter.Usage;
import com.ibm.icu.text.SimplePersonName;
import com.ibm.icu.text.SimplePersonName.Builder;

public class CheckPersonNamesTest {
    /**
     * Notes for API
     * 1. Options should be Option (singular for enums)
     * 2. All classes should have toString(). Otherwise hard to debug.
     * 3. The code has the following in  public PersonNameFormatterImpl(Locale locale, … Set<PersonNameFormatter.Options> options)
     *        // asjust for combinations of parameters that don't make sense in practice
     *        options.remove(PersonNameFormatter.Options.SORTING);
     *    That breaks if the input options are immutable. It should instead not try to modify input parameters, instead use:
     *        options = new HashSet<>(options); // or enum set
     *        options.remove(PersonNameFormatter.Options.SORTING);
     * 4. It would be useful for testing to have an @internal method to override the order with givenFirst or surnameFirst
     * 5. No enum constant com.ibm.icu.text.PersonName.FieldModifier.informal
     */

    private static final Splitter DASH_SPLITTER = Splitter.on('-');
    private static final Splitter SEMI_SPLITTER = Splitter.on(';').trimResults();
    private static final Splitter COMMA_SPLITTER = Splitter.on(',').trimResults();

    private static final Set<Options> SORTING_OPTION = ImmutableSet.of(Options.SORTING);

    public static void main(String[] args) throws IOException {
        AbstractTestLog logger = new AbstractTestLog() {

            @Override
            public void msg(String message, int level, boolean incCount, boolean newln) {
                System.out.println("Error " + message);
            }

        };
        check(logger);
    }

    static void check(AbstractTestLog logger) throws IOException {
        // just check a single file for now
        Path path = Paths.get(CLDRPaths.TEST_DATA, "personNameTest");
        Files.list(path).forEach(file -> {
            try {
                Path fileName = file.getFileName();
                String fileNameStr = fileName.toString();
                if (fileNameStr.endsWith(".txt")) {
                    LineHandler handler = new LineHandler(fileNameStr, logger);
                    Files.lines(file).forEach(handler::handleLine);
                }
            } catch (IOException e) {
                logger.errln("Failure with " + file + "\t" + e);
            }
        });

    }
    static class LineHandler {
        final Locale locale;
        SimplePersonName personName;
        String expectedResult;
        Builder personNameBuilder = SimplePersonName.builder();
        NameField nameField = null;
        AbstractTestLog testIcuPersonNames;
        boolean skipSameExpectedValue = false;

        public LineHandler(String fileNameStr, AbstractTestLog testIcuPersonNames) {
            String localeStr = fileNameStr.substring(0, fileNameStr.length()-4); // remove suffix .txt
            locale = new Locale(localeStr);
            this.testIcuPersonNames = testIcuPersonNames;
        }

        void handleLine(String line) {
            if (line.isBlank() || line.startsWith("#")) {
                return;
            }
            Set<Options> options = null;
            options = new HashSet<>(Arrays.asList(Options.SORTING));

            Iterator<String> fields = SEMI_SPLITTER.split(line).iterator();

            switch(fields.next()) {
            case "enum":
                // TODO
                String type = fields.next();
                List<String> values = COMMA_SPLITTER.splitToList(fields.next());
                break;

            case "name":
                // # name ; <field> ; <value>
                Set<FieldModifier> modifiers = new LinkedHashSet<>();

                String field = fields.next();
                String fieldValue = fields.next();

                if (field.equals("locale")) {
                    personNameBuilder.setLocale(new Locale(fieldValue));
                } else {
                    nameField = null;
                    DASH_SPLITTER.split(field).forEach(fieldPart -> {
                        if (nameField == null) { // handle first one specially
                            nameField = NameField.valueOf(fieldPart.toUpperCase(Locale.ROOT));
                        } else {
                            modifiers.add(FieldModifier.valueOf(fieldPart.toUpperCase(Locale.ROOT)));
                        }
                    });
                    personNameBuilder.addField(nameField, modifiers, fieldValue);
                }
                break;

            case "expectedResult":
                // # expectedResult; <value>
                expectedResult = fields.next();
                personName = personNameBuilder.build();
                skipSameExpectedValue = false; // suppress duplicate errors
                break;

            case "parameters":
                // # parameters; <order>; <length>; <usage>; <formality>
                // Handle order specially
                String order = fields.next();
                switch(order) {
                case "sorting":
                    options = SORTING_OPTION;
                    break;
                case "n/a":
                    options = ImmutableSet.of();
                }

                Length length = Length.valueOf(fields.next().toUpperCase(Locale.ROOT));
                Usage usage = Usage.valueOf(fields.next().toUpperCase(Locale.ROOT));
                Formality formality = Formality.valueOf(fields.next().toUpperCase(Locale.ROOT));

                PersonNameFormatter formatter = PersonNameFormatter.builder()
                    .setLocale(locale)
                    .setOptions(new HashSet(options)) // HACK because PNF requires mutability
                    .setLength(length)
                    .setUsage(usage)
                    .setFormality(formality)
                    .build();

                String actual = formatter.formatToString(personName);

                if (!skipSameExpectedValue && !Objects.equals(expectedResult, actual)) { testIcuPersonNames.errln(
                    locale
                    + ", " + personName.getNameLocale()
                    + ", " + options
                    + ", " + length
                    + ", " + usage
                    + ", " + formality
                    + ": expected: \"" + expectedResult
                    + "\" actual: \"" + actual
                    + "\""
                    );
                    skipSameExpectedValue = true;
                }
                break;

            case "endName":
                // get ready for the next name
                personNameBuilder = SimplePersonName.builder();
                break;
            }
            if (fields.hasNext()) {
                String nextValue = fields.next();
                testIcuPersonNames.errln("handled all fields: expected: \"\" actual: \"" + nextValue + "\"");
            }
        }
    }
}