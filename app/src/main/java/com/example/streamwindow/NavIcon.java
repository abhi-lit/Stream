package com.example.streamwindow;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small vector glyphs; no bitmap downloads or emoji font dependency. */
final class NavIcon extends Drawable {
    private final boolean anime;
    private final int size;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    NavIcon(boolean anime, int size) { this.anime=anime; this.size=size; }
    @Override public int getIntrinsicWidth(){return size;}
    @Override public int getIntrinsicHeight(){return size;}
    @Override public void draw(Canvas canvas) {
        canvas.save(); canvas.translate(getBounds().left,getBounds().top);
        canvas.scale(getBounds().width()/24f,getBounds().height()/24f);
        paint.setColor(Color.rgb(120,215,199)); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2);
        if(anime) {
            Path star=new Path();
            star.moveTo(12,2); star.lineTo(15,9); star.lineTo(22,12); star.lineTo(15,15);
            star.lineTo(12,22); star.lineTo(9,15); star.lineTo(2,12); star.lineTo(9,9); star.close();
            canvas.drawPath(star,paint);
        } else {
            canvas.drawRoundRect(2,4,22,20,3,3,paint);
            Path play=new Path(); play.moveTo(10,8); play.lineTo(16,12); play.lineTo(10,16); play.close();
            canvas.drawPath(play,paint);
        }
        canvas.restore();
    }
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
