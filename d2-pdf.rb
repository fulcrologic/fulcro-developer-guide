require 'asciidoctor/extensions'
require 'open3'
require 'securerandom'

Asciidoctor::Extensions.register do
  block do
    named :d2
    on_context :listing
    process do |parent, reader, attrs|
      logger.info "D2 block found and processing started..."
      d2_code = reader.source
      d2_path = (attrs['d2-executable'] || '/path/to/d2/executable').to_s
      
      logger.info "D2 executable path: #{d2_path}"
      logger.info "D2 code:\n#{d2_code}"

      svg_output, status = Open3.capture2(d2_path, '-s', '-', stdin_data: d2_code)

      if status.success?
        logger.info "D2 diagram generated successfully"

        # Save the SVG output to a file
        svg_file = "#{SecureRandom.hex}.svg"
        File.write(svg_file, svg_output)

        # Create an image block referencing the SVG file
        create_image_block parent, attrs.merge('target' => svg_file)
      else
        logger.error "Failed to generate D2 diagram: #{status.exitstatus}"
        logger.error "D2 output: #{svg_output}"
        nil
      end
    end
  end
end
