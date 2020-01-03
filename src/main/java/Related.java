import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

@Getter @Setter
@AllArgsConstructor
public class Related {
    private SelectionKey relatedChanel = null;
    private boolean isClient;
    private int countMes;
    private ByteBuffer writeThis;
    private ByteBuffer readThis;
    private int connectionNum;
}
