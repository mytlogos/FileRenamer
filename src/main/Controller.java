package main;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.stream.Collectors;

public class Controller {

    private static final DataFormat SERIALIZED_MIME_TYPE = new DataFormat("application/x-java-serialized-object");
    @FXML
    private Text currentDir;
    @FXML
    private TableView<FileWrapper> fileTable;
    @FXML
    private TableColumn<FileWrapper, String> dateColumn;
    @FXML
    private TableColumn<FileWrapper, String> nameColumn;

    private File lastDir;

    public void initialize() {
        nameColumn.setCellValueFactory(param -> param.getValue().currentNameProperty);

        dateColumn.setCellValueFactory(param -> new SimpleStringProperty(
                LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(param.getValue().file.lastModified()),
                        ZoneId.systemDefault()
                ).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))
        ));

        this.fileTable.setRowFactory(param -> {
            TableRow<FileWrapper> row = new TableRow<>();

            row.setOnDragDetected(event -> {
                if (row.isEmpty()) {
                    return;
                }
                Dragboard db = row.startDragAndDrop(TransferMode.MOVE);
                db.setDragView(row.snapshot(null, null));

                ClipboardContent cc = new ClipboardContent();

                Integer index = row.getIndex();
                cc.put(SERIALIZED_MIME_TYPE, index);
                db.setContent(cc);

                event.consume();
            });

            row.setOnDragOver(event -> {
                Dragboard db = event.getDragboard();

                if (db.hasContent(SERIALIZED_MIME_TYPE) && row.getIndex() != (Integer) db.getContent(SERIALIZED_MIME_TYPE)) {
                    event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                    event.consume();
                }
            });

            row.setOnDragDropped(event -> {
                Dragboard db = event.getDragboard();
                if (db.hasContent(SERIALIZED_MIME_TYPE)) {
                    int draggedIndex = (Integer) db.getContent(SERIALIZED_MIME_TYPE);
                    FileWrapper wrapper = this.fileTable.getItems().remove(draggedIndex);

                    int dropIndex;

                    if (row.isEmpty()) {
                        dropIndex = this.fileTable.getItems().size();
                    } else {
                        dropIndex = row.getIndex();
                    }

                    this.fileTable.getItems().add(dropIndex, wrapper);

                    event.setDropCompleted(true);
                    this.fileTable.getSelectionModel().select(dropIndex);
                    event.consume();
                }
            });

            return row;
        });
    }


    public void openDirChooser() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setInitialDirectory(this.lastDir);

        final File directory = chooser.showDialog(null);

        if (directory != null) {
            this.lastDir = directory;
            final File[] files = directory.listFiles(File::isFile);

            if (files == null) {
                this.fileTable.getItems().clear();
                return;
            }

            this.fileTable.getItems().setAll(Arrays.stream(files).map(FileWrapper::new).collect(Collectors.toList()));
            this.currentDir.setText(directory.getAbsolutePath());
        }
    }

    public void previewFileNames() {
        final ObservableList<FileWrapper> items = this.fileTable.getItems();

        for (int i = 0; i < items.size(); i++) {
            FileWrapper wrapper = items.get(i);
            String newName = String.format("%d - %s", i + 1, wrapper.file.getName());
            wrapper.currentNameProperty.setValue(newName);
        }
    }

    public void saveCurrentFileNames() {
        int renamed = 0;

        for (FileWrapper fileWrapper : this.fileTable.getItems()) {
            String previousName = fileWrapper.file.getName();

            try {
                fileWrapper.save();
                renamed++;
            } catch (IOException e) {
                e.printStackTrace();

                new Alert(
                        Alert.AlertType.ERROR,
                        String.format("Error renaming '%s' to '%s'", previousName, fileWrapper.currentNameProperty.get())
                ).show();
            }
        }
        new Alert(
                Alert.AlertType.INFORMATION,
                String.format("Successfully saved %d from %d", renamed, this.fileTable.getItems().size())
        ).show();
    }

    private static class FileWrapper {
        private final File file;
        private StringProperty currentNameProperty = new SimpleStringProperty();

        private FileWrapper(File file) {
            this.file = file;
            currentNameProperty.setValue(file.getName());
        }

        private void save() throws IOException {
            if (!this.file.getName().equals(this.currentNameProperty.get())) {
                final Path source = this.file.toPath();
                final Path target = source.getParent().resolve(this.currentNameProperty.get());
                Files.move(source, target);
            }
        }
    }

}
