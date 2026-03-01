package de.schliweb.makeacopy.di;

import android.content.Context;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;
import de.schliweb.makeacopy.utils.ocr.OCRHelper;

/** Hilt module that provides OCR-related dependencies. */
@Module
@InstallIn(SingletonComponent.class)
public class OcrModule {

  /**
   * Provides a new {@link OCRHelper} instance on each injection. OCRHelper holds mutable Tesseract
   * state and must not be shared as a singleton.
   */
  @Provides
  OCRHelper provideOCRHelper(@ApplicationContext Context context) {
    return new OCRHelper(context);
  }
}
