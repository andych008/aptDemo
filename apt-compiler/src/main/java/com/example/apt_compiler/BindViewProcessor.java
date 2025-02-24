package com.example.apt_compiler;

import com.example.apt_annotation.BindView;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

@SupportedSourceVersion(SourceVersion.RELEASE_7)
@SupportedAnnotationTypes("com.example.apt_annotation.BindView")
@SupportedOptions({ "debug" })
@AutoService(Processor.class)
public class BindViewProcessor extends AbstractProcessor {

    private static final String _SUFFIX = "$$Autobind";

    private Filer mFilerUtils;       // 文件管理工具类
    private Types mTypesUtils;    // 类型处理工具类
    private Elements mElementsUtils;  // Element处理工具类
    private Messager messager;
    private boolean isDebug;

    private Map<TypeElement, Set<ViewInfo>> mToBindMap = new HashMap<>(); //用于记录需要绑定的View的名称和对应的id

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        mFilerUtils = processingEnv.getFiler();
        mTypesUtils = processingEnv.getTypeUtils();
        mElementsUtils = processingEnv.getElementUtils();

        messager = processingEnv.getMessager();
        isDebug = Boolean.parseBoolean(processingEnv.getOptions().get("debug"));
    }

    /*
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        HashSet<String> supportTypes = new LinkedHashSet<>();
        supportTypes.add(BindView.class.getCanonicalName());
        return supportTypes; //将要支持的注解放入其中
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();// 表示支持最新的Java版本
    }
    */

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnv) {
        System.out.println("start process");
        if (set != null && set.size() != 0) {
            Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(BindView.class);//获得被BindView注解标记的element

            categories(elements);//对不同的Activity进行分类

            //对不同的Activity生成不同的帮助类
            for (TypeElement typeElement : mToBindMap.keySet()) {
                generateCodeByPoet(typeElement);
            }
            return true;
        }
        return false;
    }

    private void categories(Set<? extends Element> elements) {
        for (Element element : elements) {  //遍历每一个element
            VariableElement variableElement = (VariableElement) element;    //被@BindView标注的应当是变量，这里简单的强制类型转换
            TypeElement enclosingElement = (TypeElement) variableElement.getEnclosingElement(); //获取代表Activity的TypeElement
            Set<ViewInfo> views = mToBindMap.get(enclosingElement); //views储存着一个Activity中将要绑定的view的信息
            if (views == null) {    //如果views不存在就new一个
                views = new HashSet<>();
                mToBindMap.put(enclosingElement, views);
            }
            BindView bindAnnotation = variableElement.getAnnotation(BindView.class);    //获取到一个变量的注解
            int id = bindAnnotation.value();    //取出注解中的value值，这个值就是这个view要绑定的xml中的id
            String field = variableElement.getSimpleName().toString();
            log(String.format("bind : %s <--> %d", field, id));
            views.add(new ViewInfo(field, id));    //把要绑定的View的信息存进views中
        }
    }

    private void generateCodeByPoet(TypeElement typeElement) {
        String simpleName = typeElement.getSimpleName().toString();
        TypeName className = TypeName.get(typeElement.asType());//获取要绑定的View所在类的名称
        String packageName = mElementsUtils.getPackageOf(typeElement).getQualifiedName().toString(); //获取要绑定的View所在类的包名

        MethodSpec.Builder injectMethod = MethodSpec.methodBuilder("inject")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(Object.class, "target")
                .addStatement("$T substitute = ($T)target", className, className);

        for (ViewInfo viewInfo : mToBindMap.get(typeElement)) { //遍历每一个需要绑定的view
            injectMethod.addStatement("substitute.$L = substitute.findViewById($L)", viewInfo.viewName, viewInfo.id);
        }

        TypeSpec helperClass = TypeSpec.classBuilder(simpleName + _SUFFIX)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(ClassName.get("com.example.apt_api.template", "IBindHelper"))
                .addMethod(injectMethod.build())
                .build();

        JavaFile javaFile = JavaFile.builder(packageName, helperClass).build();
        try {
            javaFile.writeTo(mFilerUtils);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void log(String msg) {
        if (isDebug) {
            messager.printMessage(Diagnostic.Kind.NOTE, msg);
        }
    }



    //要绑定的View的信息载体
    class ViewInfo {
        String viewName;    //view的变量名
        int id; //xml中的id

        public ViewInfo(String viewName, int id) {
            this.viewName = viewName;
            this.id = id;
        }
    }
}


