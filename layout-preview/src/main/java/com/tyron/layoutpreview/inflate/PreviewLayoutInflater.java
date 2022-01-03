package com.tyron.layoutpreview.inflate;

import android.content.Context;
import android.util.Log;
import android.view.InflateException;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.flipkart.android.proteus.Proteus;
import com.flipkart.android.proteus.ProteusBuilder;
import com.flipkart.android.proteus.ProteusContext;
import com.flipkart.android.proteus.ProteusLayoutInflater;
import com.flipkart.android.proteus.ProteusView;
import com.flipkart.android.proteus.StringManager;
import com.flipkart.android.proteus.ViewTypeParser;
import com.flipkart.android.proteus.value.DrawableValue;
import com.flipkart.android.proteus.value.Layout;
import com.flipkart.android.proteus.value.ObjectValue;
import com.flipkart.android.proteus.value.Primitive;
import com.flipkart.android.proteus.value.Value;
import com.flipkart.android.proteus.view.UnknownView;
import com.flipkart.android.proteus.view.UnknownViewGroup;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.tyron.builder.project.api.AndroidModule;
import com.tyron.builder.project.api.Module;
import com.tyron.layout.appcompat.AppCompatModule;
import com.tyron.layout.cardview.CardViewModule;
import com.tyron.layout.constraintlayout.ConstraintLayoutModule;
import com.tyron.layoutpreview.ResourceManager;
import com.tyron.layoutpreview.convert.XmlToJsonConverter;
import com.tyron.layoutpreview.convert.adapter.ProteusTypeAdapterFactory;
import com.tyron.layoutpreview.manager.ResourceDrawableManager;
import com.tyron.layoutpreview.manager.ResourceLayoutManager;
import com.tyron.layoutpreview.manager.ResourceStringManager;
import com.tyron.layoutpreview.manager.ResourceStyleManager;

import java.io.File;
import java.io.StringReader;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import dalvik.system.DexClassLoader;

public class PreviewLayoutInflater {

    private final Context mBaseContext;
    private final Proteus mProteus;
    private final AndroidModule mProject;
    private ProteusContext mContext;

    private final ProteusLayoutInflater.Callback mCallback = new ProteusLayoutInflater.Callback() {
        @Override
        public ProteusView onUnknownViewType(ProteusContext context,
                                             ViewGroup parent,
                                             String type,
                                             Layout layout,
                                             ObjectValue data,
                                             int index) {
            return getProteusView(context, parent, type, layout, data);
        }

        private ProteusView getProteusView(ProteusContext context, ViewGroup parent, String type,
                                           Layout layout, ObjectValue data) {
            ProteusView view;
            Value children = null;
            // this View has a children, use a ViewGroup so the children can be laid out as well.
            if (layout.extras != null && layout.extras.get("children") != null) {
                view = new UnknownViewGroup(context, type);

                // remove the children attribute since we don't know how to place them
                children = layout.extras.get("children");
                layout.extras.remove("children");
            } else {
                view = new UnknownView(context, type);
            }

            // since we don't know what this view is, we can only apply attributes for an android.view.View
            ViewTypeParser<View> viewParser = context.getParser("android.view.View");
            if (viewParser != null) {

                ProteusView.Manager viewManager =
                        viewParser.createViewManager(context, view, layout, data, viewParser, parent, 0);
                view.setViewManager(viewManager);
                // create layout params for this view
                viewParser.onAfterCreateView(view, parent, -1);

                if (layout.extras != null) {
                    layout.extras.entrySet().forEach(entry -> {
                        int id = viewParser.getAttributeId(entry.getKey());
                        if (id != -1) {
                            viewParser.handleAttribute(view.getAsView(), id, entry.getValue());
                        } else {
                            if (parent != null) {
                                //noinspection unchecked
                                ViewTypeParser<View> parser = ((ProteusView) parent)
                                        .getViewManager().getViewTypeParser();
                                id = parser.getAttributeId(entry.getKey());
                                if (id != -1) {
                                    parser.handleAttribute(view.getAsView(), id, entry.getValue());
                                }
                            }
                        }
                    });
                    if (children != null) {
                        layout.extras.add("children", children);
                    }
                }
            }
            return view;
        }

        @Override
        public void onEvent(String event, Value value, ProteusView view) {

        }
    };

    private final ResourceStringManager mStringManager = new ResourceStringManager();
    private final ResourceDrawableManager mDrawableManager = new ResourceDrawableManager();
    private final ResourceLayoutManager mLayoutManager = new ResourceLayoutManager();
    private final ResourceStyleManager mStyleManager = new ResourceStyleManager();

    private ProteusLayoutInflater.ImageLoader mImageLoader = (view, name, callback) -> {
        if (name.startsWith("@drawable")) {
            DrawableValue value = mDrawableManager.get(name.substring("@drawable".length() + 1));
            if (value != null) {
                value.apply(view, mContext, null, (view::setBackground));
            }
        } else if (name.startsWith("@null")) {
            callback.setDrawable(null);
        }
    };

    public PreviewLayoutInflater(Context base, AndroidModule project) {
        mBaseContext = base;
        ProteusBuilder builder = new ProteusBuilder();
        builder.register(ConstraintLayoutModule.create());
        builder.register(CardViewModule.create());
        builder.register(AppCompatModule.create());
        registerCustomViews(builder, project);
        mProteus = builder.build();
        mProject = project;

        mContext = mProteus.createContextBuilder(base)
                .setCallback(mCallback)
                .setStringManager(mStringManager)
                .setDrawableManager(mDrawableManager)
                .setImageLoader(mImageLoader)
                .setStyleManager(mStyleManager)
                .setLayoutManager(mLayoutManager)
                .build();
        mContext.setParserFactory(new MaterialParserFactory(mContext));
        ProteusTypeAdapterFactory.PROTEUS_INSTANCE_HOLDER.setProteus(mProteus);
    }

    public CompletableFuture<PreviewLayoutInflater> parseResources(Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            ResourceManager resourceManager = new ResourceManager(mContext,
                    mProject.getAndroidResourcesDirectory(), mProject.getFileManager());
            mStringManager.setStrings(resourceManager.getStrings());
            mDrawableManager.setDrawables(resourceManager.getDrawables());
            mLayoutManager.setLayouts(resourceManager.getLayouts());
            return this;
        }, executor);
    }

    @NonNull
    public ProteusContext getContext() {
        return mContext;
    }

    public StringManager getStringManager() {
        return mStringManager;
    }

    public Optional<ProteusView> inflateLayout(@NonNull String name) {
        ProteusLayoutInflater inflater = mContext.getInflater();
        if (inflater == null) {
            return Optional.empty();
        }
        if (mContext.getLayout(name) == null) {
            return Optional.empty();
        }
        ObjectValue value = new ObjectValue();
        value.add("layout_name", new Primitive(name));
        return Optional.of(inflater.inflate(name, value));
    }

    @Deprecated
    public ProteusView inflate(String xml) throws InflateException {
        try {
            JsonObject object = new XmlToJsonConverter()
                    .convert(xml);
            return inflate(object);
        } catch (Exception e) {
            throw new InflateException("Unable to inflate layout: " + e.getMessage());
        }
    }

    /**
     * Convenience method to inflate a layout using a {@link JsonObject}
     *
     * @param object The json object to inflate
     * @return The inflated view
     */
    @Deprecated
    public ProteusView inflate(JsonObject object) {
        try {
            Value value = new ProteusTypeAdapterFactory(mContext)
                    .VALUE_TYPE_ADAPTER.read(new JsonReader(new StringReader(object.toString())), false);
            return inflate(value.getAsLayout());
        } catch (Exception e) {
            throw new InflateException("Unable to inflate layout: " + Log.getStackTraceString(e));
        }
    }

    public ProteusView inflate(Layout layout) {
        return mContext.getInflater().inflate(layout, new ObjectValue());
    }

    public void registerCustomViews(ProteusBuilder builder, Module module) {
        File customViewsDir = new File(module.getBuildDirectory(), "custom_views");
        if (!customViewsDir.exists() && !customViewsDir.mkdirs()) {
            return;
        }

        File[] jarFiles = customViewsDir.listFiles(c -> c.getName().endsWith(".jar"));
        if (jarFiles != null) {
            for (File file : jarFiles) {
                // TODO: use the class loader of the project, Load class from META-INF
                DexClassLoader loader = new DexClassLoader(file.getAbsolutePath(), mContext.getCodeCacheDir().getAbsolutePath(), null, this.getClass().getClassLoader());
            }
        }
    }
}
