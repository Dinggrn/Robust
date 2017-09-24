package com.meituan.robust.change.comparator;

import com.meituan.robust.autopatch.Config;
import com.meituan.robust.change.RobustChangeInfo;
import com.meituan.robust.change.RobustCodeChangeChecker;
import com.meituan.robust.utils.ProguardUtils;
import com.meituan.robust.utils.RobustLog;

import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javassist.CtClass;

import static com.meituan.robust.change.RobustCodeChangeChecker.getClassNode;

/**
 * Created by hedingxu on 17/9/3.
 */

public class DiffLineByLine {
    public static boolean diff(String first, String second,  ClassNode originalClass,  ClassNode updatedClass) {
        //解决lambda名字变了的问题
        String[] lineArrary1 = first.split("\\n");
        String[] lineArrary2 = second.split("\\n");

        if (lineArrary1.length == lineArrary2.length) {
            int index = 0;
            while (index < lineArrary1.length) {
                String line1 = lineArrary1[index];
                String line2 = lineArrary2[index];
                if (!isEqualReal(line1, line2, originalClass, updatedClass)) {
                    return false;
                }
                index++;
            }
            return true;
        } else {
            return false;
        }
    }

    public static boolean isEqualReal(String line1, String line2,  ClassNode originalClass,  ClassNode updatedClass) {
        if (null != line1 && null != line2) {
            if (line1.equals(line2)) {
                return true;
            } else {
//                System.err.println("line1: " + line1);
//                System.err.println("line2: " + line2);
//                if (line1.contains(originalClass.name) && line2.contains(updatedClass.name)){
//                    if (line1.replace(originalClass.name,updatedClass.name).equals(line2)){
//                        return true;
//                    }
//                }
                if (line1.contains("LDC") && line2.contains("LDC")) {
                    String md5_1 = line1.replace("LDC", "").replace("\"", "").trim();
                    String md5_2 = line2.replace("LDC", "").replace("\"", "").trim();
                    if (/*originalClass.name.contains("$$Lambda$") &&*/ ProguardUtils.isUpdateClassNameHas$$Lambda$(updatedClass.name)) {
                        if (checkMD5Valid(md5_1) && checkMD5Valid(md5_2)) {
                            return true;
                        }
                    }
//                    if (checkNumberValid(md5_1) && checkNumberValid(md5_2)){
//                        return true;
//                    }
                }

                if (ProguardUtils.isHasLambdaFactory$InLine1_Line2(line1, line2)) {
                    String lambdaClassName1 = ProguardUtils.getLambdaClassNameFromLine1(line1, originalClass.name);
                    String lambdaClassName2 = ProguardUtils.getLambdaClassNameFromLine2(line2, updatedClass.name);
                    if (null == lambdaClassName1 || null == lambdaClassName2 || "".equals(lambdaClassName1) || "".equals(lambdaClassName2)) {
                        RobustLog.log("null == lambdaClassName1 || null == lambdaClassName2 88");
                        return false;
                    }

                    ClassNode lambdaClassNode1 = getLambdaClassNodeFromOldJar(lambdaClassName1);
                    ClassNode lambdaClassNode2 = getLambdaClassNodeFromNewJar(lambdaClassName2);

                    ClassNode changedLambdaClassNode2 = null;
                    if (null != lambdaClassNode1 && null != lambdaClassNode2) {
                        CtClass lambdaCtClass = null;
                        try {
                            lambdaCtClass = Config.classPool.get(lambdaClassNode2.name.replace("/", "."));
                            if (null != lambdaCtClass) {
                                lambdaCtClass.defrost();
                                //名字设置成原来的
                                lambdaCtClass.setName(lambdaClassNode1.name.replace("/", "."));
                            }
                            byte[] bytes = lambdaCtClass.toBytecode();
                            changedLambdaClassNode2 = RobustCodeChangeChecker.getClassNode(bytes);
                        } catch (Exception e) {
                            RobustLog.log("Exception ",e);
                        }
                        try {
                            RobustChangeInfo.ClassChange classChange =
                                    RobustCodeChangeChecker.diffClass(lambdaClassNode1
                                            , changedLambdaClassNode2);
                            //名字设置回去
                            if (null != lambdaCtClass) {
                                lambdaCtClass.defrost();
                                lambdaCtClass.setName(lambdaClassNode2.name.replace("/", "."));
                            }
                            //store that  changed nothing realdy lambda class
                            Config.lambdaUnchangedReallyClassNameHashMap.put(lambdaClassNode2.name.replace("/", "."), lambdaClassNode1.name.replace("/", "."));
                            if (null == classChange) {
                                return true;
                            }
                            if (null != classChange) {
                                if (null == classChange.fieldChange && null == classChange.methodChange) {
                                    return true;
                                }
                            }
                        } catch (IOException e) {
                            RobustLog.log("Exception ",e);
                        }
                    }
                } else if (ProguardUtils.isHasNewAnonymousClass(line1, line2)) {
                    String updateAnonymousClassName = ProguardUtils.getAnonymousClassNameFromLine2(line2);
                    String originAnonymousClassName = ProguardUtils.getAnonymousClassNameFromLine2(line1);
                    if (null == updateAnonymousClassName || null == originAnonymousClassName) {
                        //ignore
                    } else {
                        if ("".equals(updateAnonymousClassName) || "".equals(originAnonymousClassName)) {
                            //ignore
                        } else {
                            ClassNode lambdaClassNode1 = getLambdaClassNodeFromOldJar(originAnonymousClassName);
                            ClassNode lambdaClassNode2 = getLambdaClassNodeFromNewJar(updateAnonymousClassName);

                            ClassNode changedLambdaClassNode2 = null;
                            if (null != lambdaClassNode1 && null != lambdaClassNode2) {
                                CtClass lambdaCtClass = null;
                                try {
                                    lambdaCtClass = Config.classPool.get(lambdaClassNode2.name.replace("/", "."));
                                    if (null != lambdaCtClass) {
                                        lambdaCtClass.defrost();
                                        //名字设置成原来的
                                        lambdaCtClass.setName(lambdaClassNode1.name.replace("/", "."));
                                    }
                                    byte[] bytes = lambdaCtClass.toBytecode();
                                    changedLambdaClassNode2 = RobustCodeChangeChecker.getClassNode(bytes);
                                } catch (Exception e) {
                                    RobustLog.log("Exception ",e);
                                }
                                try {
                                    RobustChangeInfo.ClassChange classChange =
                                            RobustCodeChangeChecker.diffClass(lambdaClassNode1
                                                    , changedLambdaClassNode2);
                                    //名字设置回去
                                    if (null != lambdaCtClass) {
                                        lambdaCtClass.defrost();
                                        lambdaCtClass.setName(lambdaClassNode2.name.replace("/", "."));
                                    }
                                    //store that  changed nothing realdy lambda class
                                    Config.lambdaUnchangedReallyClassNameHashMap.put(lambdaClassNode2.name.replace("/", "."), lambdaClassNode1.name.replace("/", "."));
                                    if (null == classChange) {
                                        return true;
                                    }
                                    if (null != classChange) {
                                        if (null == classChange.fieldChange && null == classChange.methodChange) {
                                            return true;
                                        }
                                    }
                                } catch (IOException e) {
                                    RobustLog.log("Exception ",e);
                                }
                            }
                        }
                    }
                } else if (ProguardUtils.hasAnonymousInit(line1, line2)) {
                    String anonymousClassName1 = ProguardUtils.getAnonymousClassNameByInitString(line1);
                    String anonymousClassName2 = ProguardUtils.getAnonymousClassNameByInitString(line2);
                    String anonymousDotClassName1 = anonymousClassName1.replace("/", ".").trim();
                    String anonymousDotClassName2 = anonymousClassName2.replace("/", ".").trim();
                    //compare if last equal name1 & name2
                    if (anonymousDotClassName1.equals(Config.lambdaUnchangedReallyClassNameHashMap.get(anonymousDotClassName2))) {
                        return true;
                    }
                }


//                DiffLineByLine:
//                line1:     INVOKESTATIC com/meituan/sample/SecondActivity$$Lambda$4.lambdaFactory$ (Lcom/meituan/sample/SecondActivity;)Landroid/view/View$OnClickListener;
//                line2:     INVOKESTATIC com/meituan/sample/SecondActivity$$Lambda$2.lambdaFactory$ (Lcom/meituan/sample/SecondActivity;)Landroid/view/View$OnClickListener;
//
//                DiffLineByLine:
//                line1:     INVOKESTATIC com/meituan/sample/SecondActivity$$Lambda$5.lambdaFactory$ (Lcom/meituan/sample/SecondActivity;)Landroid/view/View$OnClickListener;
//                line2:     INVOKESTATIC com/meituan/sample/SecondActivity$$Lambda$3.lambdaFactory$ (Lcom/meituan/sample/SecondActivity;)Landroid/view/View$OnClickListener;


                return false;
            }
        }
        if (null == line1 && null == line2) {
            return true;
        }
        return false;
    }

    private static ClassNode getLambdaClassNodeFromNewJar(String newLambdaClassName) {
        try {
            return getLambdaClassNode(newLambdaClassName, Config.newJar);
        } catch (IOException e) {
            RobustLog.log("Exception ",e);
        }
        return null;
    }

    private static ClassNode getLambdaClassNodeFromOldJar(String oldLambdaClassName) {
        try {
            return getLambdaClassNode(oldLambdaClassName, Config.oldJar);
        } catch (IOException e) {
            RobustLog.log("Exception ",e);
        }
        return null;
    }

    public static ClassNode getLambdaClassNode(String lambdaClassName, JarFile jarFile) throws IOException {
        if (!lambdaClassName.endsWith(".class")) {
            lambdaClassName = lambdaClassName + ".class";
        }
        JarEntry targetJarEntry = jarFile.getJarEntry(lambdaClassName);
        byte[] classBytes = new RobustCodeChangeChecker.ClassBytesJarEntryProvider(jarFile, targetJarEntry).load();
        ClassNode classNode = getClassNode(classBytes);
        return classNode;
    }

    private static boolean checkMD5Valid(String md5) {
        String patternString = "[a-z0-9]{32}";
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(md5);
        boolean matches = matcher.matches();
        return matches;
    }

    private static boolean checkNumberValid(String number) {
        String patternString = "[0-9]{3,20}";
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(number);
        boolean matches = matcher.matches();
        return matches;
    }
}
